package com.jinhakapply.gradevalidation.global.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jinhakapply.gradevalidation.global.code.ApiResponseCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

        return invalidRequest(fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            int separator = path.lastIndexOf('.');
            String field = separator >= 0 ? path.substring(separator + 1) : path;
            fieldErrors.putIfAbsent(field, violation.getMessage());
        });
        return invalidRequest(fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return invalidRequest(Map.of(exception.getName(), "올바르지 않은 값입니다."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
        return invalidRequest(Map.of(exception.getParameterName(), "필수 값입니다."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage() {
        return invalidRequest(Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize() {
        return ResponseEntity.status(413)
            .body(ApiErrorResponse.of("UPLOAD_TOO_LARGE", "업로드 가능한 파일 크기를 초과했습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        ApiResponseCode errorCode = ApiResponseCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus())
            .body(ApiErrorResponse.of(errorCode.getCode(), errorCode.getMessage()));
    }

    private ResponseEntity<ApiErrorResponse> invalidRequest(Map<String, String> fieldErrors) {
        ApiResponseCode errorCode = ApiResponseCode.INVALID_REQUEST_BODY;
        return ResponseEntity.status(errorCode.getHttpStatus())
            .body(ApiErrorResponse.of(errorCode.getCode(), errorCode.getMessage(), fieldErrors));
    }
}
