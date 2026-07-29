package com.jinhakapply.gradevalidation.admission.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.APPLICATION_SCORE_POLICY_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_APPLICATION_SCORE_INPUT;
import static com.jinhakapply.gradevalidation.global.util.TextNormalizer.normalizePolicyText;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreStatus;
import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreResult;
import com.jinhakapply.gradevalidation.admission.domain.StudentCommonEvaluationSnapshot;
import com.jinhakapply.gradevalidation.admission.domain.ScoreCalculationStep;
import com.jinhakapply.gradevalidation.admission.dto.CalculateApplicationScoreRequest;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import org.springframework.stereotype.Component;

@Component
public class HanshinQuantitativeScoreCalculator implements QuantitativeScoreCalculator {
    private static final BigDecimal MAX_TOTAL = new BigDecimal("1000");

    public boolean supports(String universityName, int admissionYear) {
        return (admissionYear == 2026 || admissionYear == 2027)
            && normalizePolicyText(universityName).contains("한신");
    }

    @Override
    public boolean supports(com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule rule) {
        return supports(rule.getUniversity().getName(), rule.getAdmissionYear());
    }

    @Override
    public ApplicationScoreResult calculate(
        com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule rule,
        String admissionTrackName,
        com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse gradeVerification,
        CalculateApplicationScoreRequest request,
        StudentCommonEvaluationSnapshot commonData
    ) {
        BigDecimal domesticBaseScore = gradeVerification == null ? null : gradeVerification.baseScore();
        return calculate(rule.getUniversity().getName(), rule.getAdmissionYear(), admissionTrackName,
            domesticBaseScore, request, commonData);
    }

    public ApplicationScoreResult calculate(
        String universityName,
        int admissionYear,
        String admissionTrackName,
        BigDecimal domesticAcademicBaseScore,
        CalculateApplicationScoreRequest request,
        StudentCommonEvaluationSnapshot commonData
    ) {
        if (!supports(universityName, admissionYear)) {
            throw CustomException.of(
                APPLICATION_SCORE_POLICY_NOT_FOUND,
                "한신대학교 2026·2027학년도 전형만 지원합니다."
            );
        }
        String track = normalizePolicyText(admissionTrackName);
        boolean essay = track.contains("논술");
        boolean talent = track.contains("참인재");
        boolean physical = track.contains("체육실기");
        boolean schoolRecommendation = track.contains("학교장추천");

        BigDecimal baseScore = resolveAcademicBaseScore(commonData, request, domesticAcademicBaseScore, essay);
        BigDecimal academicScore;
        BigDecimal attendanceScore = null;
        Integer equivalentAbsenceDays = null;
        BigDecimal additionalScore = null;
        BigDecimal maximumQuantitativeScore;
        List<String> pending = new ArrayList<>();
        List<String> ineligibilityReasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (talent) {
            if (commonData.educationBackground() == EducationBackground.GED) {
                academicScore = scale(baseScore, "6");
                warnings.add("검정고시 출신자는 출결을 반영하지 않고 검정고시 환산점수를 학생부 60%로 반영했습니다.");
            } else {
                academicScore = scale(baseScore, "5.4");
                equivalentAbsenceDays = equivalentAbsenceDays(commonData);
                attendanceScore = attendanceScore(equivalentAbsenceDays);
            }
            maximumQuantitativeScore = new BigDecimal("600");
            pending.add("면접 정성평가 400점");
        } else if (essay) {
            BigDecimal essayScore = required(request.essayScore(), "논술전형은 논술고사 취득점수가 필요합니다.");
            academicScore = scale(baseScore, "2");
            additionalScore = score(essayScore);
            maximumQuantitativeScore = MAX_TOTAL;
        } else if (physical) {
            String academicMultiplier = admissionYear == 2026 ? "6" : "4.5";
            String practicalMaximum = admissionYear == 2026 ? "400" : "550";
            academicScore = scale(baseScore, academicMultiplier);
            additionalScore = score(required(
                request.practicalScore(),
                "체육실기전형은 " + practicalMaximum + "점 만점 실기점수가 필요합니다."
            ));
            maximumQuantitativeScore = MAX_TOTAL;
        } else {
            academicScore = scale(baseScore, "10");
            maximumQuantitativeScore = MAX_TOTAL;
        }

        int action = commonData.highestActiveSchoolViolenceAction();
        long activeActionCount = commonData.schoolViolenceActions().stream()
            .filter(StudentCommonEvaluationSnapshot.SchoolViolenceAction::active).count();
        if (activeActionCount > 1) {
            warnings.add("복수의 학교폭력 조치 중 가장 높은 조치 호수를 적용했습니다.");
        }
        BigDecimal violenceDeduction = schoolViolenceDeduction(action);
        if (schoolRecommendation && action > 0) {
            ineligibilityReasons.add("학교장추천전형은 학교폭력 관련 기재사항이 있으면 추천 대상에서 제외됩니다.");
        }

        BigDecimal subtotal = academicScore
            .add(attendanceScore == null ? BigDecimal.ZERO : attendanceScore)
            .add(additionalScore == null ? BigDecimal.ZERO : additionalScore)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal afterDeduction = subtotal.subtract(violenceDeduction).max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);

        ApplicationScoreStatus status;
        BigDecimal finalScore;
        if (!ineligibilityReasons.isEmpty()) {
            status = ApplicationScoreStatus.INELIGIBLE;
            finalScore = null;
        } else if (!pending.isEmpty()) {
            status = ApplicationScoreStatus.QUALITATIVE_PENDING;
            finalScore = null;
        } else {
            status = ApplicationScoreStatus.COMPLETE;
            finalScore = afterDeduction;
        }
        List<ScoreCalculationStep> steps = List.of(
            new ScoreCalculationStep("ACADEMIC_SCORE", "교과 반영점수", "기초점수 × 전형별 반영배수",
                Map.of("기초점수", baseScore), academicScore),
            new ScoreCalculationStep("QUANTITATIVE_SUBTOTAL", "정량평가 소계", "교과 + 출결 + 추가점수",
                Map.of("교과", academicScore,
                    "출결", attendanceScore == null ? BigDecimal.ZERO : attendanceScore,
                    "추가점수", additionalScore == null ? BigDecimal.ZERO : additionalScore), subtotal),
            new ScoreCalculationStep("SCHOOL_VIOLENCE", "학교폭력 반영 후 점수",
                "정량평가 소계 - 학교폭력 감점",
                Map.of("정량평가소계", subtotal, "학교폭력감점", violenceDeduction), afterDeduction)
        );

        return new ApplicationScoreResult(
            status, score(baseScore), academicScore, equivalentAbsenceDays, attendanceScore, additionalScore,
            violenceDeduction, subtotal, afterDeduction, finalScore, maximumQuantitativeScore, MAX_TOTAL,
            List.copyOf(pending), List.copyOf(ineligibilityReasons), List.copyOf(warnings), steps
        );
    }

    private BigDecimal resolveAcademicBaseScore(
        StudentCommonEvaluationSnapshot commonData,
        CalculateApplicationScoreRequest request,
        BigDecimal domesticAcademicBaseScore,
        boolean essay
    ) {
        return switch (commonData.educationBackground()) {
            case DOMESTIC_HIGH_SCHOOL -> required(
                domesticAcademicBaseScore, "국내고 출신자는 학생부 교과성적 계산 결과가 필요합니다."
            );
            case GED -> gedConvertedScore(required(
                commonData.gedAverageScore(), "검정고시 합격자는 공통 지원자 데이터에 전 과목 평균이 필요합니다."
            ));
            case FOREIGN_HIGH_SCHOOL -> essay
                ? foreignEssayConvertedScore(required(
                    request.essayScore(), "논술전형 외국고 출신자는 논술고사 취득점수가 필요합니다."
                ))
                : new BigDecimal("96");
        };
    }

    private BigDecimal gedConvertedScore(BigDecimal averageScore) {
        BigDecimal truncated = averageScore.setScale(1, RoundingMode.DOWN);
        if (truncated.compareTo(new BigDecimal("95.1")) >= 0) return new BigDecimal("98");
        if (truncated.compareTo(new BigDecimal("90.1")) >= 0) return new BigDecimal("97");
        if (truncated.compareTo(new BigDecimal("85.1")) >= 0) return new BigDecimal("96");
        if (truncated.compareTo(new BigDecimal("80.1")) >= 0) return new BigDecimal("94");
        if (truncated.compareTo(new BigDecimal("70.1")) >= 0) return new BigDecimal("80");
        return new BigDecimal("50");
    }

    private BigDecimal foreignEssayConvertedScore(BigDecimal essayScore) {
        if (essayScore.compareTo(new BigDecimal("795")) >= 0) return new BigDecimal("100");
        if (essayScore.compareTo(new BigDecimal("787")) >= 0) return new BigDecimal("99");
        if (essayScore.compareTo(new BigDecimal("772")) >= 0) return new BigDecimal("98");
        if (essayScore.compareTo(new BigDecimal("752")) >= 0) return new BigDecimal("97");
        if (essayScore.compareTo(new BigDecimal("728")) >= 0) return new BigDecimal("96");
        if (essayScore.compareTo(new BigDecimal("708")) >= 0) return new BigDecimal("95");
        if (essayScore.compareTo(new BigDecimal("693")) >= 0) return new BigDecimal("94");
        if (essayScore.compareTo(new BigDecimal("685")) >= 0) return new BigDecimal("80");
        return new BigDecimal("50");
    }

    private Integer equivalentAbsenceDays(StudentCommonEvaluationSnapshot commonData) {
        if (commonData.attendance().isEmpty()) {
            throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT, "공통 지원자 데이터에 출결을 등록해 주세요.");
        }
        int absences = commonData.totalUnexcusedAbsenceDays();
        int tardy = commonData.totalUnexcusedTardyCount();
        int earlyLeave = commonData.totalUnexcusedEarlyLeaveCount();
        int classAbsence = commonData.totalUnexcusedClassAbsenceCount();
        return absences + (tardy + earlyLeave + classAbsence) / 3;
    }

    private BigDecimal attendanceScore(int absenceDays) {
        if (absenceDays <= 3) return new BigDecimal("60");
        if (absenceDays <= 6) return new BigDecimal("58");
        if (absenceDays <= 9) return new BigDecimal("56");
        if (absenceDays <= 14) return new BigDecimal("54");
        return new BigDecimal("52");
    }

    private BigDecimal schoolViolenceDeduction(int action) {
        if (action <= 3) return BigDecimal.ZERO.setScale(2);
        if (action <= 5) return new BigDecimal("3.00");
        if (action <= 7) return new BigDecimal("5.00");
        return new BigDecimal("20.00");
    }

    private BigDecimal required(BigDecimal value, String message) {
        if (value == null) throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT, message);
        return value;
    }

    private BigDecimal scale(BigDecimal score, String multiplier) {
        return score.multiply(new BigDecimal(multiplier)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal score(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

}
