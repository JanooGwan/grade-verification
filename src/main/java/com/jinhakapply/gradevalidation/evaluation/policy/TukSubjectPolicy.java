package com.jinhakapply.gradevalidation.evaluation.policy;

import java.math.BigDecimal;

/** 한국공학대 과목 선택과 저장 결과의 탐구 선택 근거에 공통으로 적용한다. */
public final class TukSubjectPolicy {
    public static final String MISSING_GRADE_REASON = "등급 또는 환산 가능한 성취도 정보가 없습니다.";

    private TukSubjectPolicy() {}

    public static boolean isKoreanHistory(String courseName) {
        return courseName != null && courseName.replaceAll("\\s", "").equals("한국사");
    }

    public static boolean countsForInquiryCredits(BigDecimal effectiveGrade, String exclusionReason) {
        // 이수단위 비교에는 등급 없는 이수과목도 포함하되 미반영 학기·교과 등은 제외한다.
        // 기존 저장 결과에도 동일한 제외 사유가 기록되어 있다.
        return effectiveGrade != null || MISSING_GRADE_REASON.equals(exclusionReason);
    }
}
