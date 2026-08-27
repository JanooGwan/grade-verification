package com.jinhakapply.gradevalidation.admission.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_APPLICATION_SCORE_INPUT;
import static com.jinhakapply.gradevalidation.global.util.TextNormalizer.normalizePolicyText;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreResult;
import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreStatus;
import com.jinhakapply.gradevalidation.admission.domain.StudentCommonEvaluationSnapshot;
import com.jinhakapply.gradevalidation.admission.domain.ScoreCalculationStep;
import com.jinhakapply.gradevalidation.admission.dto.CalculateApplicationScoreRequest;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GedSubjectType;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import org.springframework.stereotype.Component;

/** 모집요강에서 정량식이 확정된 한국공학대·명지전문대·경복대·삼육대 전형 계산기. */
@Component
public class GuidebookQuantitativeScoreCalculator implements QuantitativeScoreCalculator {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    @Override
    public boolean supports(EvaluationRule rule) {
        String university = normalizePolicyText(rule.getUniversity().getName());
        return ((rule.getAdmissionYear() == 2026 || rule.getAdmissionYear() == 2027)
            && university.contains("한국공학"))
            || (rule.getAdmissionYear() == 2027 && (university.contains("명지전문")
            || university.contains("삼육")))
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
        String university = normalizePolicyText(rule.getUniversity().getName());
        String admissionTrack = normalizePolicyText(admissionTrackName);
        String recruitmentUnit = normalizePolicyText(rule.getRecruitmentUnit());
        String track = normalizePolicyText(admissionTrackName + " " + rule.getAdmissionType() + " " + rule.getRecruitmentUnit());
        List<String> pending = new ArrayList<>();
        List<String> ineligible = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<ScoreCalculationStep> steps = new ArrayList<>();

        BigDecimal baseScore = resolveBaseScore(
            rule, university, track, gradeVerification, commonData, request, pending, warnings, steps
        );
        BigDecimal academicScore = baseScore == null ? ZERO : score(baseScore.multiply(rule.getScoreMultiplier()));
        BigDecimal attendanceScore = null;
        Integer equivalentAbsenceDays = null;
        BigDecimal additionalScore = null;
        BigDecimal maximumTotal = score(new BigDecimal("100").multiply(rule.getScoreMultiplier()));
        BigDecimal maximumQuantitative = maximumTotal;

        if (university.contains("한국공학") && track.contains("논술")) {
            maximumQuantitative = new BigDecimal("500.00");
            maximumTotal = new BigDecimal("500.00");
            if (request.essayScore() == null) {
                pending.add("논술고사 400점");
            } else {
                validateTukEssayScore(request.essayScore());
                additionalScore = score(request.essayScore());
                steps.add(step("TUK_ESSAY_SCORE", "한국공학대 논술고사 반영점수", "논술고사 취득점수",
                    Map.of("논술고사점수", request.essayScore()), additionalScore));
            }
        }
        if (university.contains("명지전문") && track.contains("항공서비스")) {
            pending.add("면접 정성평가 600점");
            maximumTotal = new BigDecimal("1000.00");
        }
        if (university.contains("경복")) {
            BigDecimal academicMaximum = maximumTotal;
            BigDecimal allowedBonus = kbuAllowedBonus(track);
            BigDecimal requestedBonus = request.bonusScore() == null ? BigDecimal.ZERO : request.bonusScore();
            if (requestedBonus.signum() < 0 || requestedBonus.compareTo(allowedBonus) > 0
                || requestedBonus.remainder(new BigDecimal("5")).signum() != 0) {
                throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT,
                    "경복대학교 해당 모집단위의 KBU입시드림포인트는 0점 이상 " + allowedBonus
                        + "점 이하에서 5점 단위로 입력해야 합니다.");
            }
            additionalScore = score(requestedBonus);
            maximumTotal = score(new BigDecimal("100").add(allowedBonus));
            maximumQuantitative = score(academicMaximum.add(allowedBonus));
            if (isKbuInterviewTrack(track)) {
                pending.add("면접 정성평가 60점");
            } else if (isKbuPracticalTrack(track)) {
                pending.add("실기고사 80점");
            }
        }
        if (university.contains("삼육") && isSyuAthleticTalent(admissionTrack, recruitmentUnit)) {
            equivalentAbsenceDays = equivalentAbsenceDays(commonData);
            BigDecimal attendanceBase = syuAttendanceBase(equivalentAbsenceDays);
            attendanceScore = score(attendanceBase.multiply(new BigDecimal("0.40")));
            steps.add(step("SYU_ATTENDANCE_SCORE", "삼육대 출결 반영점수", "출결 기본점수 × 40%",
                Map.of("환산결석일수", BigDecimal.valueOf(equivalentAbsenceDays),
                    "출결기본점수", attendanceBase,
                    "반영비율", new BigDecimal("0.40")), attendanceScore));
            maximumQuantitative = new BigDecimal("400.00");
            maximumTotal = new BigDecimal("1000.00");
            pending.add("1단계 수상실적 600점");
            pending.add("2단계 면접 200점");
            warnings.add("예체능인재 체육학과 2단계는 1단계 성적 80%와 면접 20%를 합산합니다.");
        } else if (university.contains("삼육")
            && isSyuPracticalTrack(admissionTrack, recruitmentUnit)) {
            BigDecimal practicalMaximum = recruitmentUnit.contains("아트앤디자인")
                ? new BigDecimal("800.00") : new BigDecimal("600.00");
            maximumQuantitative = score(new BigDecimal("100").multiply(rule.getScoreMultiplier()));
            maximumTotal = new BigDecimal("1000.00");
            pending.add("실기고사 " + practicalMaximum.setScale(0).toPlainString() + "점");
        }

        int action = commonData.highestActiveSchoolViolenceAction();
        BigDecimal violenceDeduction = schoolViolenceDeduction(
            university, track, admissionTrack, action, ineligible
        );
        if (rule.getAdmissionYear() == 2026 && university.contains("한국공학")
            && action > 0 && !track.contains("지역균형")) {
            warnings.add(
                "2026 모집요강은 호수별 상세 감점값을 기재하지 않아, 동일 기준을 구체화한 2027 모집요강의 1~9호 감점표를 적용했습니다."
            );
        }
        BigDecimal subtotal = academicScore
            .add(attendanceScore == null ? BigDecimal.ZERO : attendanceScore)
            .add(additionalScore == null ? BigDecimal.ZERO : additionalScore)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal afterDeduction = subtotal.subtract(violenceDeduction).max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);
        steps.add(step("ACADEMIC_SCORE", "교과 반영점수", "기초점수 × 배수",
            Map.of("기초점수", baseScore == null ? BigDecimal.ZERO : baseScore,
                "배수", rule.getScoreMultiplier()), academicScore));
        steps.add(step("QUANTITATIVE_SUBTOTAL", "정량평가 소계", "교과 + 출결 + 추가점수",
            Map.of("교과", academicScore,
                "출결", attendanceScore == null ? BigDecimal.ZERO : attendanceScore,
                "추가점수", additionalScore == null ? BigDecimal.ZERO : additionalScore), subtotal));
        steps.add(step("SCHOOL_VIOLENCE", "학교폭력 반영 후 점수", "정량평가 소계 - 학교폭력 감점",
            Map.of("정량평가소계", subtotal, "학교폭력감점", violenceDeduction), afterDeduction));

        ApplicationScoreStatus status = !ineligible.isEmpty() ? ApplicationScoreStatus.INELIGIBLE
            : !pending.isEmpty() ? ApplicationScoreStatus.QUALITATIVE_PENDING : ApplicationScoreStatus.COMPLETE;
        BigDecimal finalScore = status == ApplicationScoreStatus.COMPLETE ? afterDeduction : null;
        return new ApplicationScoreResult(status, score(baseScore == null ? BigDecimal.ZERO : baseScore), academicScore,
            equivalentAbsenceDays, attendanceScore, additionalScore, violenceDeduction, subtotal, afterDeduction,
            finalScore, maximumQuantitative, maximumTotal, List.copyOf(pending), List.copyOf(ineligible),
            List.copyOf(warnings), List.copyOf(steps));
    }

    private BigDecimal resolveBaseScore(
        EvaluationRule rule,
        String university,
        String track,
        GradeVerificationResponse verification,
        StudentCommonEvaluationSnapshot commonData,
        CalculateApplicationScoreRequest request,
        List<String> pending,
        List<String> warnings,
        List<ScoreCalculationStep> steps
    ) {
        if (university.contains("한국공학") && track.contains("논술")
            && usesTukEssayComparisonScore(rule, commonData)) {
            if (request.essayScore() == null) return BigDecimal.ZERO;
            validateTukEssayScore(request.essayScore());
            BigDecimal result = tukEssayComparisonScore(request.essayScore());
            steps.add(step("TUK_ESSAY_COMPARISON_SCORE", "한국공학대 논술 비교내신 환산점수",
                "논술고사 총점을 비교내신 구간표에 대입",
                Map.of("논술고사점수", request.essayScore()), result));
            return result;
        }
        if (commonData.educationBackground() == EducationBackground.DOMESTIC_HIGH_SCHOOL) {
            if (verification == null) {
                throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT, "국내고 교과성적 계산 결과가 필요합니다.");
            }
            return verification.baseScore();
        }
        if (university.contains("명지전문")) {
            if (commonData.educationBackground() == EducationBackground.FOREIGN_HIGH_SCHOOL) return new BigDecimal("20");
            if (!commonData.gedSubjectScores().isEmpty()) {
                return scoreForAverageGrade(rule, weightedGedAverageGrade(
                    commonData, this::mjcGedGrade, true, "MJC_GED_WEIGHTED_AVERAGE", steps));
            }
            warnings.add("과목별 검정고시 점수가 없어 기존 전 과목 평균점수로 임시 환산했습니다.");
            return scoreForGrade(rule, mjcGedGrade(requiredGedAverage(commonData)));
        }
        if (university.contains("한국공학")) {
            if (commonData.educationBackground() == EducationBackground.GED) {
                if (!commonData.gedSubjectScores().isEmpty()) {
                    return tukGedAverageScore(rule, track, commonData, steps);
                }
                warnings.add("과목별 검정고시 점수가 없어 기존 전 과목 평균점수로 임시 환산했습니다.");
                return scoreForGrade(rule, tukGedGrade(requiredGedAverage(commonData)));
            }
        }
        if (university.contains("경복") && commonData.educationBackground() == EducationBackground.GED) {
            if (commonData.gedSubjectScores().isEmpty()) {
                throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT,
                    "경복대학교 검정고시 환산에는 필수 6개 과목의 과목별 점수가 필요합니다.");
            }
            BigDecimal averageGrade = weightedGedAverageGrade(
                commonData, this::kbuGedGrade, false, "KBU_GED_AVERAGE", steps);
            BigDecimal grade = averageGrade.setScale(1, RoundingMode.DOWN);
            steps.add(step("KBU_GED_TRUNCATION", "경복대 검정고시 평균등급 절사",
                "평균등급을 소수 첫째 자리까지 절사", Map.of("절사전평균등급", averageGrade), grade));
            return scoreForAverageGrade(rule, grade);
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
        BigDecimal convertedScore = rule.getGradeScores().get(grade);
        if (convertedScore == null) {
            throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT,
                "적용 규칙에 " + grade + "등급 환산점수가 없습니다.");
        }
        return convertedScore;
    }

    private BigDecimal scoreForAverageGrade(EvaluationRule rule, BigDecimal grade) {
        BigDecimal bounded = grade.max(BigDecimal.ONE).min(new BigDecimal("9"));
        int lower = bounded.setScale(0, RoundingMode.FLOOR).intValue();
        int upper = bounded.setScale(0, RoundingMode.CEILING).intValue();
        BigDecimal lowerScore = rule.getGradeScores().get(lower);
        if (lower == upper) return lowerScore;
        BigDecimal fraction = bounded.subtract(BigDecimal.valueOf(lower));
        return lowerScore.add(rule.getGradeScores().get(upper).subtract(lowerScore).multiply(fraction));
    }

    private BigDecimal weightedGedAverageGrade(
        StudentCommonEvaluationSnapshot data,
        java.util.function.ToIntFunction<BigDecimal> gradeConverter,
        boolean useMjcWeights,
        String stepKey,
        List<ScoreCalculationStep> steps
    ) {
        java.util.Set<GedSubjectType> available = data.gedSubjectScores().stream()
            .map(StudentCommonEvaluationSnapshot.GedSubjectScore::subjectType)
            .collect(java.util.stream.Collectors.toSet());
        java.util.Set<GedSubjectType> required = java.util.Set.of(
            GedSubjectType.KOREAN, GedSubjectType.ENGLISH, GedSubjectType.MATH,
            GedSubjectType.KOREAN_HISTORY, GedSubjectType.SOCIAL, GedSubjectType.SCIENCE
        );
        if (!available.containsAll(required)) {
            throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT,
                "검정고시 환산에는 국어·영어·수학·한국사·사회·과학 점수가 모두 필요합니다.");
        }
        if (!useMjcWeights) {
            boolean duplicateRequiredSubject = required.stream().anyMatch(type -> data.gedSubjectScores().stream()
                .filter(subject -> subject.subjectType() == type).count() != 1);
            if (duplicateRequiredSubject) {
                throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT,
                    "경복대학교 검정고시 필수과목은 과목별로 정확히 1개 점수만 등록해야 합니다.");
            }
        }
        BigDecimal gradeTimesUnits = BigDecimal.ZERO;
        BigDecimal units = BigDecimal.ZERO;
        for (StudentCommonEvaluationSnapshot.GedSubjectScore subject : data.gedSubjectScores()) {
            if (!useMjcWeights && subject.subjectType() == GedSubjectType.ELECTIVE) continue;
            BigDecimal weight = BigDecimal.valueOf(useMjcWeights ? gedUnits(subject.subjectType()) : 1);
            gradeTimesUnits = gradeTimesUnits.add(
                BigDecimal.valueOf(gradeConverter.applyAsInt(subject.score())).multiply(weight)
            );
            units = units.add(weight);
        }
        if (units.signum() == 0) {
            throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT, "반영 가능한 검정고시 과목별 점수가 없습니다.");
        }
        BigDecimal average = gradeTimesUnits.divide(units, 5, RoundingMode.HALF_UP);
        steps.add(step(stepKey, "검정고시 과목별 환산등급 가중평균",
            "Σ(환산등급 × 단위수) ÷ Σ(단위수)",
            Map.of("환산등급단위합", gradeTimesUnits, "단위수합", units), average));
        return average;
    }

    private int gedUnits(GedSubjectType type) {
        return switch (type) {
            case KOREAN, ENGLISH, MATH -> 6;
            case KOREAN_HISTORY, SOCIAL, SCIENCE -> 4;
            case ELECTIVE -> 2;
        };
    }

    private BigDecimal tukGedAverageScore(EvaluationRule rule, String track,
        StudentCommonEvaluationSnapshot data, List<ScoreCalculationStep> steps) {
        java.util.Set<GedSubjectType> reflected = track.contains("경영")
            ? java.util.Set.of(GedSubjectType.KOREAN, GedSubjectType.ENGLISH, GedSubjectType.MATH, GedSubjectType.SOCIAL)
            : java.util.Set.of(GedSubjectType.KOREAN, GedSubjectType.ENGLISH, GedSubjectType.MATH, GedSubjectType.SCIENCE);
        List<BigDecimal> scores = data.gedSubjectScores().stream()
            .filter(subject -> reflected.contains(subject.subjectType()))
            .map(subject -> scoreForGrade(rule, tukGedGrade(subject.score())))
            .toList();
        boolean exactlyOnePerSubject = reflected.stream().allMatch(type -> data.gedSubjectScores().stream()
            .filter(subject -> subject.subjectType() == type).count() == 1);
        if (!exactlyOnePerSubject || scores.size() != 4) {
            throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT,
                "한국공학대학교 검정고시 환산에는 국어·영어·수학과 계열별 과학/사회 점수가 모두 필요합니다.");
        }
        BigDecimal convertedScoreSum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal result = convertedScoreSum.divide(
            BigDecimal.valueOf(scores.size()), rule.getIntermediateScale(), rule.getIntermediateRounding());
        steps.add(step("TUK_GED_SUBJECT_AVERAGE", "한국공학대 검정고시 반영과목 평균",
            "Σ(과목별 환산점수) ÷ 반영과목수",
            Map.of("과목별환산점수합", convertedScoreSum, "반영과목수", BigDecimal.valueOf(scores.size())), result));
        return result;
    }

    private boolean usesTukEssayComparisonScore(EvaluationRule rule, StudentCommonEvaluationSnapshot data) {
        if (data.educationBackground() != EducationBackground.DOMESTIC_HIGH_SCHOOL) return true;
        if (data.graduationStatus() != GraduationStatus.GRADUATE
            || data.graduationYear() == null) return false;
        int comparisonCutoffYear = rule.getAdmissionYear() == 2026 ? 2024 : 2025;
        return data.graduationYear() <= comparisonCutoffYear;
    }

    private void validateTukEssayScore(BigDecimal essayScore) {
        if (essayScore.signum() < 0 || essayScore.compareTo(new BigDecimal("400")) > 0) {
            throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT,
                "한국공학대학교 논술고사 점수는 0점 이상 400점 이하로 입력해야 합니다.");
        }
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
        String admissionTrack,
        int action,
        List<String> ineligible
    ) {
        if (action < 0 || action > 9) {
            throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT,
                "학교폭력 조치호수는 0호 이상 9호 이하여야 합니다.");
        }
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
        if (university.contains("삼육")) return syuSchoolViolence(admissionTrack, action, ineligible);
        return ZERO;
    }

    private BigDecimal syuSchoolViolence(String admissionTrack, int action, List<String> ineligible) {
        if (admissionTrack.equals("학교장추천")) {
            if (action >= 4) ineligible.add("학교장추천전형은 학교폭력 4호 이상 처분 시 지원 자격이 제한됩니다.");
            return action <= 3 ? new BigDecimal("5.00") : ZERO;
        }
        if (admissionTrack.contains("논술") || admissionTrack.contains("실기우수자")) {
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

    private BigDecimal kbuAllowedBonus(String track) {
        boolean susiBalanceOpportunity = track.contains("수시")
            && (track.contains("기회균형") || track.contains("기초생활")
                || track.contains("차상위") || track.contains("한부모"));
        boolean noBonusCollegeGraduateHealth = track.contains("전문대학이상졸업자")
            && (track.contains("간호") || track.contains("임상병리") || track.contains("물리치료"));
        if (susiBalanceOpportunity || noBonusCollegeGraduateHealth) return BigDecimal.ZERO;
        return isKbuHealthTrack(track) ? new BigDecimal("5") : new BigDecimal("10");
    }

    private boolean isKbuInterviewTrack(String track) {
        return track.contains("항공서비스") || track.contains("준오헤어디자인");
    }

    private boolean isKbuPracticalTrack(String track) {
        return track.contains("실용음악") || track.contains("공연예술");
    }

    private boolean isSyuAthleticTalent(String admissionTrack, String recruitmentUnit) {
        return admissionTrack.equals("예체능인재") && recruitmentUnit.contains("체육학과");
    }

    private boolean isSyuPracticalTrack(String admissionTrack, String recruitmentUnit) {
        boolean practicalUnit = recruitmentUnit.contains("아트앤디자인")
            || recruitmentUnit.contains("체육학과");
        return practicalUnit && (admissionTrack.equals("학교장추천") || admissionTrack.equals("농어촌"));
    }

    private BigDecimal score(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private ScoreCalculationStep step(String key, String description, String formula,
        Map<String, BigDecimal> operands, BigDecimal result) {
        return new ScoreCalculationStep(key, description, formula, operands, result);
    }

}
