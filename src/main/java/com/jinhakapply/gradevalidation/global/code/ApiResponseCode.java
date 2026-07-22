package com.jinhakapply.gradevalidation.global.code;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ApiResponseCode {

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "예상하지 못한 오류가 발생했습니다."),

    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다."),
    UNIVERSITY_NOT_FOUND(HttpStatus.NOT_FOUND, "대학교를 찾을 수 없습니다."),
    DUPLICATE_UNIVERSITY_CODE(HttpStatus.CONFLICT, "이미 등록된 대학교 코드입니다."),
    EVALUATION_RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "성적 반영 규칙을 찾을 수 없습니다."),
    DUPLICATE_EVALUATION_RULE(HttpStatus.CONFLICT, "동일한 대학·연도·전형·모집단위·버전의 규칙이 이미 존재합니다."),
    INVALID_EVALUATION_RULE(HttpStatus.BAD_REQUEST, "성적 반영 규칙이 올바르지 않습니다."),
    INVALID_EVALUATION_RULE_STATUS(HttpStatus.CONFLICT, "현재 상태에서는 성적 반영 규칙을 변경할 수 없습니다."),
    INVALID_RULE_EXTRACTION_FILE(HttpStatus.BAD_REQUEST, "모집요강 PDF 파일이 올바르지 않습니다."),
    RULE_EXTRACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "규칙 추출 결과를 찾을 수 없습니다."),
    INVALID_RULE_EXTRACTION_STATUS(HttpStatus.CONFLICT, "현재 상태에서는 추출 결과로 초안을 만들 수 없습니다."),
    INVALID_TRANSCRIPT_FILE(HttpStatus.BAD_REQUEST, "학생부 Excel 파일이 올바르지 않습니다."),
    TRANSCRIPT_IMPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "학생부 가져오기 이력을 찾을 수 없습니다."),
    TRANSCRIPT_STUDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "학생부가 등록된 학생을 찾을 수 없습니다."),
    ADMISSION_TRACK_NOT_FOUND(HttpStatus.NOT_FOUND, "전형을 찾을 수 없습니다."),
    RECRUITMENT_UNIT_NOT_FOUND(HttpStatus.NOT_FOUND, "모집단위를 찾을 수 없습니다."),
    DUPLICATE_ADMISSION_TRACK(HttpStatus.CONFLICT, "같은 대학·연도에 동일한 전형이 이미 존재합니다."),
    DUPLICATE_RECRUITMENT_UNIT(HttpStatus.CONFLICT, "같은 전형에 동일한 모집단위가 이미 존재합니다."),
    STUDENT_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "학생 지원 정보를 찾을 수 없습니다."),
    DUPLICATE_STUDENT_APPLICATION(HttpStatus.CONFLICT, "동일한 학생 지원 정보가 이미 존재합니다."),
    INVALID_STUDENT_APPLICATION(HttpStatus.BAD_REQUEST, "학생 지원 정보가 올바르지 않습니다."),
    MATCHING_EVALUATION_RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "지원 정보에 맞는 게시 규칙을 찾을 수 없습니다."),
    CONFLICTING_EVALUATION_RULES(HttpStatus.CONFLICT, "지원 정보에 적용 가능한 게시 규칙이 여러 개입니다."),
    APPLICATION_SCORE_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "지원 전형에 적용할 정량평가 총점 정책을 찾을 수 없습니다."),
    INVALID_APPLICATION_SCORE_INPUT(HttpStatus.BAD_REQUEST, "전형 총점 계산 입력값이 올바르지 않습니다."),
    INVALID_STUDENT_COMMON_DATA(HttpStatus.BAD_REQUEST, "대학 공통 지원자 데이터가 올바르지 않습니다."),
    TRANSCRIPT_COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "학생부 과목을 찾을 수 없습니다."),
    DUPLICATE_TRANSCRIPT_COURSE(HttpStatus.CONFLICT, "같은 학년·학기·교과·과목이 이미 존재합니다."),
    DUPLICATE_RULE_EXTRACTION_FILE(HttpStatus.CONFLICT, "같은 모집요강 파일이 이미 분석되었습니다."),
    VERIFICATION_RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "성적 검증 이력을 찾을 수 없습니다."),
    VERIFICATION_RESULT_EXPORT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "성적 검증 결과 Excel 파일을 생성하지 못했습니다."),
    AI_ASSISTANT_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "AI 도우미 설정이 완료되지 않았습니다."),
    AI_ASSISTANT_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "AI 모델 응답을 처리하지 못했습니다."),
    AI_ASSISTANT_DATABASE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "AI 도우미용 데이터베이스 조회에 실패했습니다."),
    AI_ASSISTANT_UNSAFE_QUERY(HttpStatus.BAD_REQUEST, "안전하지 않은 데이터베이스 조회가 차단되었습니다.");

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
