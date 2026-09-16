package com.jinhakapply.gradevalidation.admission.service;

import java.time.YearMonth;

import com.jinhakapply.gradevalidation.admission.domain.StudentCommonEvaluationSnapshot;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.global.util.TextNormalizer;

/** 논술 비교내신 판단을 교과 선계산 여부와 최종 합산에서 공유한다. */
public final class TukEssayPolicy {
    public enum Mode { TRANSCRIPT, COMPARISON, GRADUATION_DATE_REQUIRED }

    private TukEssayPolicy() {}

    public static Mode mode(EvaluationRule rule, String track, StudentCommonEvaluationSnapshot data) {
        if ((rule.getAdmissionYear() != 2026 && rule.getAdmissionYear() != 2027)
            || !TextNormalizer.normalizePolicyText(rule.getUniversity().getName()).contains("한국공학")
            || !TextNormalizer.normalizePolicyText(track).contains("논술")) return Mode.TRANSCRIPT;
        if (data.educationBackground() != EducationBackground.DOMESTIC_HIGH_SCHOOL) return Mode.COMPARISON;
        if (data.graduationStatus() != GraduationStatus.GRADUATE) return Mode.TRANSCRIPT;
        int cutoffYear = comparisonTranscriptCutoffYear(rule.getAdmissionYear());
        if (data.graduationDate() != null) {
            return YearMonth.from(data.graduationDate()).compareTo(YearMonth.of(cutoffYear, 2)) <= 0
                ? Mode.COMPARISON : Mode.TRANSCRIPT;
        }
        if (data.graduationYear() == null || data.graduationYear() == cutoffYear) {
            return Mode.GRADUATION_DATE_REQUIRED;
        }
        return data.graduationYear() < cutoffYear ? Mode.COMPARISON : Mode.TRANSCRIPT;
    }

    /** 모집요강 연도별 논술 비교내신 경계. 해당 연도 2월 졸업자까지 비교내신 대상이다. */
    private static int comparisonTranscriptCutoffYear(int admissionYear) {
        return switch (admissionYear) {
            case 2026 -> 2024;
            case 2027 -> 2025;
            default -> throw new IllegalArgumentException("지원하지 않는 한국공학대 논술 모집연도입니다: " + admissionYear);
        };
    }
}
