package com.jinhakapply.gradevalidation.global.code;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ApiResponseCode {

    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다."),
    UNIVERSITY_NOT_FOUND(HttpStatus.NOT_FOUND, "대학교를 찾을 수 없습니다."),
    DUPLICATE_UNIVERSITY_CODE(HttpStatus.CONFLICT, "이미 등록된 대학교 코드입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ApiResponseCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String getCode() {
        return name();
    }
}
