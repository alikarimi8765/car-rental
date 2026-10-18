package com.example.carrental.nl;

import static com.example.carrental.nl.IssueKind.AMBIGUOUS;
import static com.example.carrental.nl.IssueKind.MISSING;
import static com.example.carrental.nl.IssueKind.UNSUPPORTED;
import static com.example.carrental.nl.ParserLexicon.CI;
import static com.example.carrental.nl.ParserLexicon.NUM_WORD_RE;
import static com.example.carrental.nl.ParserLexicon.SUPPORTED_ALIASES;
import static com.example.carrental.nl.ParserLexicon.SUPPORTED_LIST;
import static com.example.carrental.nl.ParserLexicon.UNSUPPORTED_ALIASES;
import static com.example.carrental.nl.ParserLexicon.alternation;
import static com.example.carrental.nl.ParserLexicon.toNumber;
import static com.example.carrental.nl.RequestField.CAR_TYPE;

import com.example.carrental.model.CarType;
import com.example.carrental.nl.ParseSupport.Found;
import com.example.carrental.nl.ParseSupport.Scratch;

import java.util.Locale;
import java.util.OptionalDouble;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Extracts a single car type from residual NL text. */
final class CarTypeExtractor {

    // Optional quantity ("2 SUVs") followed by any known vehicle word.
    private static final String VEHICLE_RE = alternation(
            Stream.concat(SUPPORTED_ALIASES.keySet().stream(), UNSUPPORTED_ALIASES.stream()).toList(), true);
    private static final Pattern CAR = Pattern.compile(
            "(?:(?<![\\w.:/-])(\\d+|" + NUM_WORD_RE + ")\\s+)?\\b(" + VEHICLE_RE + ")\\b", CI);
    private static final Pattern VAGUE_QUANTITY = Pattern.compile(
            "\\b(?:a\\s+couple(?:\\s+of)?|a\\s+few|several)\\s+(" + VEHICLE_RE + ")\\b", CI);

    private static final String ONE_CAR_ONLY = "'%s': only one car can be reserved per request.";

    private CarTypeExtractor() {
    }

    static Found<CarType> extract(String input) {
        Scratch scratch = new Scratch(input);
        TreeSet<CarType> supported = new TreeSet<>();
        TreeSet<String> unsupported = new TreeSet<>();

        rejectVagueQuantities(scratch);
        collectVehicles(scratch, supported, unsupported);
        return resolve(scratch, supported, unsupported);
    }

    private static void rejectVagueQuantities(Scratch scratch) {
        scratch.reject(VAGUE_QUANTITY, UNSUPPORTED, CAR_TYPE, ONE_CAR_ONLY);
    }

    private static void collectVehicles(
            Scratch scratch, TreeSet<CarType> supported, TreeSet<String> unsupported) {
        scratch.forEach(CAR, m -> {
            String quantity = m.group(1);
            String raw = m.group(2);
            String alias = canonicalAlias(raw);
            CarType car = SUPPORTED_ALIASES.get(alias);
            if (car == null) {
                unsupported.add(alias);
                return;
            }
            supported.add(car);
            if (quantity != null) {
                OptionalDouble n = toNumber(quantity);
                if (n.isPresent() && n.getAsDouble() != 1) {
                    scratch.issues().add(new Issue(UNSUPPORTED, CAR_TYPE,
                            ONE_CAR_ONLY.formatted(quantity + " " + raw)));
                }
            }
        });
    }

    private static Found<CarType> resolve(
            Scratch scratch, TreeSet<CarType> supported, TreeSet<String> unsupported) {
        if (!unsupported.isEmpty()) {
            scratch.issues().add(new Issue(UNSUPPORTED, CAR_TYPE,
                    "'%s' is not available. Supported types: %s."
                            .formatted(String.join(", ", unsupported), SUPPORTED_LIST)));
            return scratch.empty();
        }
        if (supported.size() > 1) {
            String names = supported.stream().map(CarType::label).sorted().collect(Collectors.joining(", "));
            scratch.issues().add(new Issue(AMBIGUOUS, CAR_TYPE,
                    "Several car types mentioned (%s); which one do you want?".formatted(names)));
            return scratch.empty();
        }
        if (supported.size() == 1) {
            // Quantity ≠ 1 (or vague quantity) already produced issues — do not fill carType.
            return scratch.hasIssues() ? scratch.empty() : scratch.done(supported.first());
        }
        if (!scratch.hasIssues()) {
            scratch.issues().add(new Issue(MISSING, CAR_TYPE,
                    "No car type found. Supported types: %s.".formatted(SUPPORTED_LIST)));
        }
        return scratch.empty();
    }

    private static String canonicalAlias(String raw) {
        String alias = raw.toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", " ").strip();
        if (!isKnownAlias(alias) && alias.endsWith("s") && isKnownAlias(alias.substring(0, alias.length() - 1))) {
            alias = alias.substring(0, alias.length() - 1);
        }
        return alias;
    }

    private static boolean isKnownAlias(String alias) {
        return SUPPORTED_ALIASES.containsKey(alias) || UNSUPPORTED_ALIASES.contains(alias);
    }
}
