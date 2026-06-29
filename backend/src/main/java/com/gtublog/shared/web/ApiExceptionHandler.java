package com.gtublog.shared.web;

import java.net.URI;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException exception) {
        return problemDetail(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.", exception.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail handleNotFound(NoSuchElementException exception) {
        return problemDetail(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다.", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        return problemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "예상하지 못한 오류가 발생했습니다.",
                "요청을 처리하지 못했습니다.");
    }

    private ProblemDetail problemDetail(HttpStatus status, String title, String detail) {
        var problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create("https://gtublog.dev/problems/" + status.value()));
        return problemDetail;
    }
}
