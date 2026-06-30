package com.gtublog.shared.web;

import java.net.URI;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException exception) {
        return problemDetail(HttpStatus.BAD_REQUEST, "Bad request", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException exception) {
        return problemDetail(HttpStatus.BAD_REQUEST, "Bad request", exception.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException exception) {
        return problemDetail(HttpStatus.UNAUTHORIZED, "Authentication failed", "Invalid credentials.");
    }

    @ExceptionHandler(com.gtublog.auth.RateLimitExceededException.class)
    ProblemDetail handleRateLimit(com.gtublog.auth.RateLimitExceededException exception) {
        return problemDetail(HttpStatus.TOO_MANY_REQUESTS, "Too many requests", exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        return problemDetail(HttpStatus.FORBIDDEN, "Access denied", "You do not have permission to access this resource.");
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail handleNotFound(NoSuchElementException exception) {
        return problemDetail(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConflict(DataIntegrityViolationException exception) {
        return problemDetail(HttpStatus.CONFLICT, "Conflict", "The request conflicts with current data state.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return problemDetail(HttpStatus.BAD_REQUEST, "Bad request", "The request body is malformed or inconsistent.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidMethodArgument(MethodArgumentNotValidException exception) {
        return problemDetail(HttpStatus.BAD_REQUEST, "Bad request", "The request body violates the API contract.");
    }

    @ExceptionHandler(com.gtublog.automation.TerminalSubmissionConflictException.class)
    ProblemDetail handleTerminalConflict(com.gtublog.automation.TerminalSubmissionConflictException exception) {
        return problemDetail(HttpStatus.CONFLICT, "Terminal submission conflict", exception.getMessage());
    }

    @ExceptionHandler(com.gtublog.automation.GenerationLeaseLostException.class)
    ProblemDetail handleLeaseLost(com.gtublog.automation.GenerationLeaseLostException exception) {
        return problemDetail(HttpStatus.CONFLICT, "Generation lease lost", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled API exception", exception);
        return problemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error",
                "The server could not process the request.");
    }

    private ProblemDetail problemDetail(HttpStatus status, String title, String detail) {
        var problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create("https://gtublog.dev/problems/" + status.value()));
        return problemDetail;
    }
}
