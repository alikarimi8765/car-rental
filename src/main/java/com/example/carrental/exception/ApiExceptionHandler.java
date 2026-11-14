package com.example.carrental.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * One error path for the whole API, always an RFC 9457 {@link ProblemDetail}.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring's own failures (unknown URL,
 * wrong method, unsupported media type) keep their proper 404/405/415 status instead of being
 * swallowed by the catch-all. Expected domain failures map to 404/409/422; request shape
 * problems to 400; anything else is logged with its stack trace and answered with a generic 500
 * that carries only the correlation id, never internals.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ReservationNotFoundException.class)
    public ProblemDetail notFound(ReservationNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(CarUnavailableException.class)
    public ProblemDetail unavailable(CarUnavailableException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidReservationException.class)
    public ProblemDetail invalid(InvalidReservationException e) {
        return problem(HttpStatusCode.valueOf(422), e.getMessage());
    }

    /** A constraint the database enforces that validation did not catch first. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail dataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation", e);
        return problem(HttpStatus.CONFLICT, "The request conflicts with the stored data.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e) {
        log.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error. Quote the correlation id when reporting it.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::describe)
                .sorted()
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request: " + String.join("; ", errors));
        problem.setProperty("errors", errors);
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "The request body is missing or is not valid JSON.");
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    /** Adds the correlation id to bodies Spring builds itself (404, 405, 415, ...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(e, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            addCorrelationId(problem);
        }
        return response;
    }

    private static String describe(FieldError error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : error.getField() + " is invalid";
    }

    private static ProblemDetail problem(HttpStatusCode status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        addCorrelationId(problem);
        return problem;
    }

    private static void addCorrelationId(ProblemDetail problem) {
        String id = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (id != null) {
            problem.setProperty("correlationId", id);
        }
    }
}
