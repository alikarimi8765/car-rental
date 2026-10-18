# Car Rental – technical exercise (Java 25 / Spring Boot)

A simulated car rental system: capacity-limited reservations of three car types
(sedan, SUV, van) plus a rule-based natural-language interface that turns
*"Reserve an SUV from 15 October 2026 at 10:00 for 3 days"* into structured data
and reports anything **missing, ambiguous, unsupported or invalid** instead of guessing.

Requirements: **JDK 25**, Maven 3.6.3+ (Spring Boot 4.1.1).

```
mvn verify              # build + all tests
mvn spring-boot:run     # REST API on :8080
```

```bash
# natural language -> reservation (201), or what is unclear (422), or fleet full (409)
curl -s -X POST localhost:8080/api/reservations/nl -H 'Content-Type: application/json' \
  -d '{"text":"Reserve an SUV from 15 October 2026 at 10:00 for 3 days","customer":"Ada"}'

# structured booking / list / cancel
curl -s -X POST localhost:8080/api/reservations -H 'Content-Type: application/json' \
  -d '{"carType":"van","start":"2026-10-15T10:00","days":2}'
curl -s localhost:8080/api/reservations
curl -s -X DELETE localhost:8080/api/reservations/<reservation-uuid>
```
(The example dates are in 2026; once they are in the past the service will rightly refuse them.)

Fleet size is stored in the `fleet_capacity` table. On first startup (empty table), rows are
seeded from `rental.fleet.sedan|suv|van` in `application.properties` (default 5 / 3 / 2).

## Layout

| Package | Responsibility | Spring? |
|---|---|---|
| `model` | `CarType`, `Reservation` (record, half-open `[start, end)`), `Fleet`, `Availability`, `ReservationRules` | no |
| `model.entity` | `ReservationEntity`, `FleetCapacityEntity` | yes |
| `repository` | `ReservationStore`, `ReservationRepository`, `FleetCapacityRepository`, `JpaReservationStore` | yes |
| `mapper` | `ReservationMapper`, `BookingRequestMapper`, `AssistantResponseMapper`, `ApiMapper` | yes |
| `service` | `CarRentalService`, `RentalAssistant`, `AssistantResponse` | mixed |
| `util` | `HumanDates` | no |
| `nl` | `ReservationParser`: text → `ParseResult` (structured data + `Issue`s) | no |
| `controller` | `ReservationController`, `ApiDtos` | yes |
| `exception` | `RentalException` hierarchy, `ApiExceptionHandler` | mixed |
| `config` | `RentalConfig` (`Clock`, `Fleet`, `TransactionTemplate` beans), `RentalProperties` | yes |

`CarRentalService`, `RentalAssistant`, `ReservationParser`, and `JpaReservationStore` use Spring stereotypes
(`@Service` / `@Component` / `@Repository`). Reservations are always stored in H2 via JPA.

## Design decisions worth discussing

### 1. Capacity is tracked per car *type*, not per physical car
A reservation `[s, e)` is accepted iff at every instant in the window fewer than `capacity`
bookings of that type are active. For intervals this is exactly the condition under which all
bookings can be assigned to distinct cars, so choosing a plate can be deferred to pick-up time.
Assigning a specific car at booking time (first-fit) is the obvious design but is wrong: it
rejects requests that become satisfiable if earlier bookings are re-shuffled.

### 2. Peak concurrency, not overlap count
A new booking may overlap many existing ones and still fit if they are never needed at the same
time (`peakConcurrencyMattersNotOverlapCount`). Load only *rises* at a reservation start, so the
peak inside the window is found by evaluating at `start` and at every overlapping reservation's
start. Back-to-back rentals (return 10:00, pick-up 10:00) are allowed.

### 3. Suggested alternatives are provably complete
`earliestAvailableStart` only tries the requested time and the moments existing bookings end:
if start `c` is feasible but slightly earlier ones are not, only a sliver before `c` changed,
and it can only be saturated if a booking ends exactly at `c`. The assistant uses this to answer
*"…the earliest start that works is 18 October 2026 at 10:00."*

### 4. Check-then-insert is atomic
`reserve` holds a `ReentrantLock` across the availability check and the database commit.
The JPA transaction finishes before the lock is released, so a second request cannot read the table
before the first insert is visible. A Spring test fires concurrent requests at 3 SUVs and asserts exactly 3 succeed.

### 5. The NL parser never guesses silently
Every field is extracted unambiguously or reported as an `Issue(kind, field, message)`:

| Kind | Examples |
|---|---|
| `MISSING` | no car type / date / time / duration |
| `AMBIGUOUS` | "SUV or van"; `at 10` (10:00 or 22:00?); `03/04/2026`; "on Friday"; "15 October" (no year); "Friday 15 October 2026" (it's a Thursday); two different durations |
| `UNSUPPORTED` | truck/convertible/…; "5 hours"; "1.5 days"; "2 SUVs"; cancel/modify requests |
| `INVALID` | 31 February; 25:00; 13pm; 0 days; "in 99999999999 days" |

All issues are reported at once, partial results are kept, and the assistant only books when the
parse is complete and issue-free. The parser is a pipeline of small passes that **mask** what they
consume (dates → durations → times → car type), so one number is never counted in two roles.
Relative dates ("tomorrow", "in 3 days") use an injected `java.time.Clock`, shared with the
service so "tomorrow" and "in the past" always agree.

### 6. Modern-Java choices
* **Sealed `AssistantResponse`** (`Reserved` / `NeedsClarification` / `Rejected`) and a pattern-matching
  `switch` in the controller: the compiler enforces that every outcome is mapped to an HTTP status.
* **Records** for values (`Reservation`, `Issue`, `ParseResult`, DTOs) – immutable by construction.
* **Sealed `RentalException`** so the full set of failure modes is visible and closed.
* DTOs use plain strings (ISO-8601) so the JSON does not depend on Jackson's `java.time` handling.

### HTTP mapping

| Situation | Status |
|---|---|
| booked | 201 |
| request unclear / violates a rule (nothing booked) | 422 |
| understood, but no free car of that type | 409 |
| cancel unknown id | 404 |

## Known limitations / trade-offs

* **H2, one process.** Reservations are rows in `./data/carrental`. The lock does not coordinate
  several application processes. Fleet sizes live in the `fleet_capacity` table (seeded from
  `rental.fleet.*` only when that table is empty) and are loaded into a `Fleet` bean at startup.
* **Naive local date-times** (`LocalDateTime`); no time zones or DST – a "day" is always 24 h.
* **No turnaround/cleaning buffer** between rentals, no pricing, locations, customers or payments;
  `customer` is a free-text label.
* `peakLoad` is O(n²) in the overlapping reservations of one type – fine at fleet scale; a sweep
  line would make it O(n log n).
* **Parser is English-only and regex-based.** It covers common phrasings but not arbitrary language
  ("a couple of days", "the 15th", "a week from Friday", "until the 18th"); unrecognised text is
  reported as missing rather than guessed at. A production system would put an LLM or NER model
  behind the same `ParseResult` contract and keep these rules as the validation layer.
* A missing year is *asked about* (with a suggestion) rather than silently assumed;
  `HH:MM` without am/pm is read as 24-hour time (documented and tested).
* One car per request; quantity > 1 is reported as unsupported. Cancellation exists on the service
  and REST API but not in the NL layer ("cancel…" is detected and refused).
* No authentication, rate limiting or request-size limits on the REST API.
