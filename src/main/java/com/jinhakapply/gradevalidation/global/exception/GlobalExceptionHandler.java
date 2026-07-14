package com.jinhakapply.gradevalidation.global.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jinhakapply.gradevalidation.global.code.ApiResponseCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
}
