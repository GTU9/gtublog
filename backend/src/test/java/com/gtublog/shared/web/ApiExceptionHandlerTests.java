package com.gtublog.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTests {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsIllegalArgumentExceptionToProblemDetail() {
        var problemDetail = handler.handleBadRequest(new IllegalArgumentException("slug is required"));

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getTitle()).isEqualTo("잘못된 요청입니다.");
        assertThat(problemDetail.getDetail()).isEqualTo("slug is required");
    }
}
