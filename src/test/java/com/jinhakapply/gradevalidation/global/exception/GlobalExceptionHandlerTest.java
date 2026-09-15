package com.jinhakapply.gradevalidation.global.exception;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INTERNAL_SERVER_ERROR;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.UPLOAD_TOO_LARGE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.UNIVERSITY_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void explainsConcurrentImportWithConflictStatus() {
        var response = handler.handleCustomException(CustomException.of(
            com.jinhakapply.gradevalidation.global.code.ApiResponseCode.TRANSCRIPT_IMPORT_BUSY
        ));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.code()).isEqualTo("TRANSCRIPT_IMPORT_BUSY");
            assertThat(body.message()).contains("진행 중", "최근 가져오기");
            assertThat(body.message()).doesNotContain("예상하지 못한 오류");
        });
    }

    @Test
    void mapsCustomExceptionToItsConfiguredStatusAndDetail() {
        var response = handler.handleCustomException(CustomException.of(UNIVERSITY_NOT_FOUND, "99"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.code()).isEqualTo("UNIVERSITY_NOT_FOUND");
            assertThat(body.message()).contains("99");
            assertThat(body.fieldErrors()).isEmpty();
        });
    }

    @Test
    void hidesUnexpectedExceptionDetailsFromClient() {
        var response = handler.handleUnexpected(new IllegalStateException("database password leaked"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.code()).isEqualTo(INTERNAL_SERVER_ERROR.getCode());
            assertThat(body.message()).isEqualTo(INTERNAL_SERVER_ERROR.getMessage());
            assertThat(body.message()).doesNotContain("password", "leaked");
        });
    }

    @Test
    void mapsMaximumUploadSizeThroughTheResponseCodeCatalog() {
        var response = handler.handleMaxUploadSize();

        assertThat(response.getStatusCode()).isEqualTo(UPLOAD_TOO_LARGE.getHttpStatus());
        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.code()).isEqualTo(UPLOAD_TOO_LARGE.getCode());
            assertThat(body.message()).isEqualTo(UPLOAD_TOO_LARGE.getMessage());
        });
    }
}
