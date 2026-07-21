package com.jinhakapply.gradevalidation.admission.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_APPLICATION_SCORE_INPUT;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreResult;
import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreStatus;
import com.jinhakapply.gradevalidation.admission.domain.StudentCommonEvaluationSnapshot;
import com.jinhakapply.gradevalidation.admission.dto.CalculateApplicationScoreRequest;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import org.springframework.stereotype.Component;

/** 모집요강에서 정량식이 확정된 한국공학대·명지전문대·경복대·삼육대 전형 계산기. */
@Component
public class GuidebookQuantitativeScoreCalculator implements QuantitativeScoreCalculator {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    @Override
    public boolean supports(EvaluationRule rule) {
        String university = normalize(rule.getUniversity().getName());
        return (rule.getAdmissionYear() == 2027 && (university.contains("한국공학")
            || university.contains("명지전문") || university.contains("삼육")))
            || (rule.getAdmissionYear() == 2026 && university.contains("경복"));
    }

    @Override
    public ApplicationScoreResult calculate(
        EvaluationRule rule,
        String admissionTrackName,
        GradeVerificationResponse gradeVerification,
        CalculateApplicationScoreRequest request,
        StudentCommonEvaluationSnapshot commonData
    ) {
        String university = normalize(rule.getUniversity().getName());
        String track = normalize(admissionTrackName + " " + rule.getAdmissionType() + " " + rule.getRecruitmentUnit());
        List<String> pending = new ArrayList<>();
        List<String> ineligible = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        BigDecimal baseScore = resolveBaseScore(rule, university, track, gradeVerification, commonData, request, pending);
        BigDecimal academicScore = baseScore == null ? ZERO : score(baseScore.multiply(rule.getScoreMultiplier()));
        BigDecimal attendanceScore = null;
        Integer equivalentAbsenceDays = null;
        BigDecimal additionalScore = null;
        BigDecimal maximumTotal = score(new BigDecimal("100").multiply(rule.getScoreMultiplier()));

        if (university.contains("명지전문") && track.contains("항공서비스")) {
            pending.add("면접 정성평가 600점");
            maximumTotal = new BigDecimal("1000.00");
        }
        if (university.contains("경복")) {
            BigDecimal allowedBonus = isKbuHealthTrack(track) ? new BigDecimal("5") : new BigDecimal("10");
            BigDecimal requestedBonus = request.bonusScore() == null ? BigDecimal.ZERO : request.bonusScore();
            if (requestedBonus.compareTo(allowedBonus) > 0) {
                throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT,
                    "경복대학교 해당 모집단위의 KBU입시드림포인트 상한은 " + allowedBonus + "점입니다.");
            }
            additionalScore = score(requestedBonus);
            maximumTotal = score(new BigDecimal("100").add(allowedBonus));
        }
        if (university.contains("삼육") && isSyuArtTrack(track)) {
            equivalentAbsenceDays = equivalentAbsenceDays(commonData);
            attendanceScore = score(syuAttendanceBase(equivalentAbsenceDays).multiply(new BigDecimal("0.10")));
            academicScore = score(baseScore.multiply(new BigDecimal("0.90")));
            maximumTotal = new BigDecimal("100.00");
        }

        int action = commonData.highestActiveSchoolViolenceAction();
        BigDecimal violenceDeduction = schoolViolenceDeduction(university, track, action, ineligible);
        BigDecimal subtotal = academicScore
            .add(attendanceScore == null ? BigDecimal.ZERO : attendanceScore)
            .add(additionalScore == null ? BigDecimal.ZERO : additionalScore)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal afterDeduction = subtotal.subtract(violenceDeduction).max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);

        ApplicationScoreStatus status = !ineligible.isEmpty() ? ApplicationScoreStatus.INELIGIBLE
            : !pending.isEmpty() ? ApplicationScoreStatus.QUALITATIVE_PENDING : ApplicationScoreStatus.COMPLETE;
        BigDecimal finalScore = status == ApplicationScoreStatus.COMPLETE ? afterDeduction : null;
        return new ApplicationScoreResult(status, score(baseScore == null ? BigDecimal.ZERO : baseScore), academicScore,
            equivalentAbsenceDays, attendanceScore, additionalScore, violenceDeduction, subtotal, afterDeduction,
            finalScore, maximumTotal, maximumTotal, List.copyOf(pending), List.copyOf(ineligible),
            List.copyOf(warnings));
    }

    private BigDecimal resolveBaseScore(
        EvaluationRule rule,
        String university,
        String track,
        GradeVerificationResponse verification,
        StudentCommonEvaluationSnapshot commonData,
        CalculateApplicationScoreRequest request,
        List<String> pending
    ) {
        if (commonData.educationBackground() == EducationBackground.DOMESTIC_HIGH_SCHOOL) {
            if (verification == null) {
                throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT, "국내고 교과성적 계산 결과가 필요합니다.");
            }
            return verification.baseScore();
        }
        if (university.contains("명지전문")) {
            if (commonData.educationBackground() == EducationBackground.FOREIGN_HIGH_SCHOOL) return new BigDecimal("20");
            return scoreForGrade(rule, mjcGedGrade(requiredGedAverage(commonData)));
        }
        if (university.contains("한국공학")) {
            if (commonData.educationBackground() == EducationBackground.GED) {
                return scoreForGrade(rule, tukGedGrade(requiredGedAverage(commonData)));
            }
            if (track.contains("논술") && request.essayScore() != null) {
                return tukEssayComparisonScore(request.essayScore());
            }
        }
        if (university.contains("경복") && commonData.educationBackground() == EducationBackground.GED) {
            return scoreForGrade(rule, kbuGedGrade(requiredGedAverage(commonData)));
        }
        pending.add("모집요강상 별도 심의 또는 제출서류 확인이 필요한 비교내신");
        return BigDecimal.ZERO;
    }

    private BigDecimal requiredGedAverage(StudentCommonEvaluationSnapshot commonData) {
        if (commonData.gedAverageScore() == null) {
            throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT,
                "검정고시 지원자는 모집요강 가중치를 적용한 전 과목 평균점수가 필요합니다.");
        }
        return commonData.gedAverageScore();
    }

    private int mjcGedGrade(BigDecimal score) {
        if (score.compareTo(new BigDecimal("100")) >= 0) return 1;
        if (score.compareTo(new BigDecimal("98")) >= 0) return 2;
        if (score.compareTo(new BigDecimal("96")) >= 0) return 3;
        if (score.compareTo(new BigDecimal("94")) >= 0) return 4;
        if (score.compareTo(new BigDecimal("92")) >= 0) return 5;
        if (score.compareTo(new BigDecimal("90")) >= 0) return 6;
        if (score.compareTo(new BigDecimal("80")) >= 0) return 7;
        if (score.compareTo(new BigDecimal("70")) >= 0) return 8;
        return 9;
    }

    private int tukGedGrade(BigDecimal score) {
        if (score.compareTo(new BigDecimal("100")) >= 0) return 3;
        if (score.compareTo(new BigDecimal("95")) >= 0) return 4;
        if (score.compareTo(new BigDecimal("85")) >= 0) return 5;
        if (score.compareTo(new BigDecimal("80")) >= 0) return 6;
        if (score.compareTo(new BigDecimal("75")) >= 0) return 7;
        if (score.compareTo(new BigDecimal("70")) >= 0) return 8;
        return 9;
    }

    private int kbuGedGrade(BigDecimal score) {
        if (score.compareTo(new BigDecimal("99")) >= 0) return 1;
        if (score.compareTo(new BigDecimal("96")) >= 0) return 2;
        if (score.compareTo(new BigDecimal("91")) >= 0) return 3;
        if (score.compareTo(new BigDecimal("84")) >= 0) return 4;
        if (score.compareTo(new BigDecimal("76")) >= 0) return 5;
        if (score.compareTo(new BigDecimal("70")) >= 0) return 6;
        if (score.compareTo(new BigDecimal("65")) >= 0) return 7;
        if (score.compareTo(new BigDecimal("62")) >= 0) return 8;
        return 9;
    }

    private BigDecimal scoreForGrade(EvaluationRule rule, int grade) {
        return rule.getGradeScores().get(grade);
    }

    private BigDecimal tukEssayComparisonScore(BigDecimal essayScore) {
        if (essayScore.compareTo(new BigDecimal("395")) >= 0) return new BigDecimal("100");
        if (essayScore.compareTo(new BigDecimal("390")) >= 0) return new BigDecimal("99");
        if (essayScore.compareTo(new BigDecimal("380")) >= 0) return new BigDecimal("98");
        if (essayScore.compareTo(new BigDecimal("365")) >= 0) return new BigDecimal("97");
        if (essayScore.compareTo(new BigDecimal("350")) >= 0) return new BigDecimal("96");
        if (essayScore.compareTo(new BigDecimal("340")) >= 0) return new BigDecimal("94");
        if (essayScore.compareTo(new BigDecimal("320")) >= 0) return new BigDecimal("80");
        if (essayScore.compareTo(new BigDecimal("300")) >= 0) return new BigDecimal("60");
        return new BigDecimal("25");
    }

    private BigDecimal schoolViolenceDeduction(
        String university,
        String track,
        int action,
        List<String> ineligible
    ) {
        if (action == 0) return ZERO;
        if (university.contains("한국공학")) {
            if (track.contains("지역균형")) {
                ineligible.add("학교폭력 조치사항이 있는 지역균형전형 지원자는 지원 자격 미달입니다.");
                return ZERO;
            }
            int[] deductions = {0, 10, 15, 20, 30, 45, 60, 80, 90, 100};
            return score(BigDecimal.valueOf(deductions[action]));
        }
        if (university.contains("명지전문")) {
            if (action >= 8) ineligible.add("학교폭력 8호 또는 9호 처분자는 지원할 수 없습니다.");
            return ZERO;
        }
        if (university.contains("경복")) {
            if (action <= 3) return ZERO;
            return action <= 5 ? new BigDecimal("5.00") : new BigDecimal("10.00");
        }
        if (university.contains("삼육")) return syuSchoolViolence(track, action, ineligible);
        return ZERO;
    }

    private BigDecimal syuSchoolViolence(String track, int action, List<String> ineligible) {
        if (track.contains("학교장추천")) {
            if (action >= 4) ineligible.add("학교장추천전형은 학교폭력 4호 이상 처분 시 지원 자격이 제한됩니다.");
            return action <= 3 ? new BigDecimal("5.00") : ZERO;
        }
        if (track.contains("논술") || track.contains("실기우수자")) {
            if (action <= 3) return new BigDecimal("10.00");
            if (action <= 5) return new BigDecimal("30.00");
            if (action <= 7) return new BigDecimal("50.00");
            return new BigDecimal("100.00");
        }
        if (action <= 3) return new BigDecimal("5.00");
        if (action <= 5) return new BigDecimal("10.00");
        if (action <= 7) return new BigDecimal("20.00");
        return new BigDecimal("100.00");
    }

    private int equivalentAbsenceDays(StudentCommonEvaluationSnapshot data) {
        if (data.attendance().isEmpty()) {
            throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT, "삼육대학교 예체능인재전형은 출결 정보가 필요합니다.");
        }
        return data.totalUnexcusedAbsenceDays()
            + (data.totalUnexcusedTardyCount() + data.totalUnexcusedEarlyLeaveCount()
            + data.totalUnexcusedClassAbsenceCount()) / 3;
    }

    private BigDecimal syuAttendanceBase(int days) {
        if (days <= 3) return new BigDecimal("100");
        if (days <= 7) return new BigDecimal("98");
        if (days <= 12) return new BigDecimal("96");
        if (days <= 20) return new BigDecimal("94");
        if (days <= 40) return new BigDecimal("90");
        return BigDecimal.ZERO;
    }

    private boolean isKbuHealthTrack(String track) {
        return track.contains("간호") || track.contains("치위생") || track.contains("작업치료")
            || track.contains("임상병리") || track.contains("물리치료");
    }

    private boolean isSyuArtTrack(String track) {
        return track.contains("예체능인재") || track.contains("아트앤디자인") || track.contains("체육학과");
    }

    private BigDecimal score(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
