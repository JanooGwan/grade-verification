package com.jinhakapply.gradevalidation.global.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jinhakapply.gradevalidation.global.code.ApiResponseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiErrorResponse> handleCustomException(CustomException exception) {
        ApiResponseCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
            .body(ApiErrorResponse.of(errorCode.getCode(), exception.getFullMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ApiResponseCode errorCode = ApiResponseCode.INVALID_REQUEST_BODY;
        return ResponseEntity.status(errorCode.getHttpStatus())
            .body(ApiErrorResponse.of(errorCode.getCode(), errorCode.getMessage(), fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        ApiResponseCode errorCode = ApiResponseCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus())
            .body(ApiErrorResponse.of(errorCode.getCode(), errorCode.getMessage()));
    }
}
