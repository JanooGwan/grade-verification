package com.jinhakapply.gradevalidation.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreStatus;
import com.jinhakapply.gradevalidation.admission.domain.StudentCommonEvaluationSnapshot;
import com.jinhakapply.gradevalidation.admission.dto.CalculateApplicationScoreRequest;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.global.code.ApiResponseCode;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.transcript.domain.GedSubjectType;
import com.jinhakapply.gradevalidation.university.domain.University;
import org.junit.jupiter.api.Test;

class GuidebookQuantitativeScoreCalculatorTest {
    private final GuidebookQuantitativeScoreCalculator calculator = new GuidebookQuantitativeScoreCalculator();

    @Test
    void appliesTukFiveHundredPointScaleAndSchoolViolenceDeduction() {
        var result = calculator.calculate(rule("TUK", "한국공학대학교", 2027, "5", scores(100, 99, 98, 97, 96, 94, 80, 60, 25)),
            "학생부교과(교과우수자)", verification("98.1267"), request(null),
            common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 6));

        assertThat(result.academicScore()).isEqualByComparingTo("490.63");
        assertThat(result.schoolViolenceDeduction()).isEqualByComparingTo("60.00");
        assertThat(result.finalScore()).isEqualByComparingTo("430.63");
    }

    @Test
    void makesTukRegionalApplicantWithViolenceActionIneligible() {
        var result = calculator.calculate(rule("TUK", "한국공학대학교", 2027, "5", scores(100, 99, 98, 97, 96, 94, 80, 60, 25)),
            "학생부교과(지역균형)", verification("98"), request(null),
            common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 1));

        assertThat(result.status()).isEqualTo(ApplicationScoreStatus.INELIGIBLE);
        assertThat(result.finalScore()).isNull();
    }

    @Test
    void convertsMjcGedWeightedAverageAndKeepsAviationInterviewPending() {
        var result = calculator.calculate(rule("MJC", "명지전문대학교", 2027, "4", scores(100, 90, 80, 70, 60, 50, 40, 30, 20)),
            "정원내 특별전형(어학우수자) 항공서비스과", null, request(null),
            common(EducationBackground.GED, "96.4", 0, 0));

        assertThat(result.academicBaseScore()).isEqualByComparingTo("80.00");
        assertThat(result.academicScore()).isEqualByComparingTo("320.00");
        assertThat(result.status()).isEqualTo(ApplicationScoreStatus.QUALITATIVE_PENDING);
        assertThat(result.pendingComponents()).containsExactly("면접 정성평가 600점");
    }

    @Test
    void reproducesMjcGuidebookGedThirtyFourUnitGoldenExample() {
        var common = new StudentCommonEvaluationSnapshot(
            EducationBackground.GED, com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType.GENERAL,
            GraduationStatus.GRADUATE, null, null,
            List.of(
                ged(GedSubjectType.KOREAN, "국어", "87"), ged(GedSubjectType.ENGLISH, "영어", "82"),
                ged(GedSubjectType.MATH, "수학", "78"), ged(GedSubjectType.KOREAN_HISTORY, "한국사", "84"),
                ged(GedSubjectType.SOCIAL, "사회", "94"), ged(GedSubjectType.SCIENCE, "과학", "73"),
                ged(GedSubjectType.ELECTIVE, "선택1", "77"), ged(GedSubjectType.ELECTIVE, "선택2", "95")
            ), List.of(), List.of(), List.of()
        );
        var result = calculator.calculate(
            rule("MJC", "명지전문대학교", 2027, "4", scores(100, 90, 80, 70, 60, 50, 40, 30, 20)),
            "정원내 일반전형", null, request(null), common
        );

        assertThat(result.academicBaseScore()).isEqualByComparingTo("41.76");
        assertThat(result.academicScore()).isEqualByComparingTo("167.06");
        assertThat(result.warnings()).isEmpty();
        assertThat(result.calculationSteps()).filteredOn(step -> step.key().equals("MJC_GED_WEIGHTED_AVERAGE"))
            .singleElement().satisfies(step -> {
                assertThat(step.operands().get("환산등급단위합")).isEqualByComparingTo("232");
                assertThat(step.operands().get("단위수합")).isEqualByComparingTo("34");
                assertThat(step.result()).isEqualByComparingTo("6.82353");
            });
    }

    @Test
    void appliesKbuBonusLimitAndViolenceDeduction() {
        EvaluationRule rule = rule("KBOK", "경복대학교", 2026, "1", scores(100, 87.5, 75, 62.5, 50, 37.5, 25, 12.5, 0));
        var result = calculator.calculate(rule, "학생부교과 일반학과", verification("87.5"), request("8"),
            common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 5));

        assertThat(result.additionalScore()).isEqualByComparingTo("8.00");
        assertThat(result.schoolViolenceDeduction()).isEqualByComparingTo("5.00");
        assertThat(result.finalScore()).isEqualByComparingTo("90.50");

        var healthBoundary = calculator.calculate(rule, "간호학과", verification("87.5"), request("5"),
            common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 0));
        var generalBoundary = calculator.calculate(rule, "일반학과", verification("87.5"), request("10"),
            common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 0));
        assertThat(healthBoundary.additionalScore()).isEqualByComparingTo("5.00");
        assertThat(generalBoundary.additionalScore()).isEqualByComparingTo("10.00");

        assertThatThrownBy(() -> calculator.calculate(rule, "간호학과", verification("87.5"), request("6"),
            common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 0)))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INVALID_APPLICATION_SCORE_INPUT));
        assertThatThrownBy(() -> calculator.calculate(rule, "일반학과", verification("87.5"), request("-1"),
            common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 0)))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INVALID_APPLICATION_SCORE_INPUT));
    }

    @Test
    void convertsKbuGedRequiredSubjectsAndTruncatesAverageGrade() {
        EvaluationRule rule = rule("KBOK", "경복대학교", 2026, "1",
            scores(100, 87.5, 75, 62.5, 50, 37.5, 25, 12.5, 0));
        StudentCommonEvaluationSnapshot common = new StudentCommonEvaluationSnapshot(
            EducationBackground.GED, com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType.GENERAL,
            GraduationStatus.GRADUATE, 2025, null,
            List.of(
                ged(GedSubjectType.KOREAN, "국어", "99"),
                ged(GedSubjectType.ENGLISH, "영어", "98"),
                ged(GedSubjectType.MATH, "수학", "95"),
                ged(GedSubjectType.KOREAN_HISTORY, "한국사", "90"),
                ged(GedSubjectType.SOCIAL, "사회", "83"),
                ged(GedSubjectType.SCIENCE, "과학", "75")
            ), List.of(), List.of(), List.of()
        );

        var result = calculator.calculate(rule, "수시 일반 일반학과", null, request(null), common);

        assertThat(result.academicBaseScore()).isEqualByComparingTo("68.75");
        assertThat(result.finalScore()).isEqualByComparingTo("68.75");
        assertThat(result.calculationSteps()).filteredOn(step -> step.key().equals("KBU_GED_AVERAGE"))
            .singleElement().satisfies(step -> assertThat(step.result()).isEqualByComparingTo("3.50000"));
    }

    @Test
    void rejectsKbuGedAverageWithoutRequiredSubjectScores() {
        EvaluationRule rule = rule("KBOK", "경복대학교", 2026, "1",
            scores(100, 87.5, 75, 62.5, 50, 37.5, 25, 12.5, 0));

        assertThatThrownBy(() -> calculator.calculate(rule, "수시 일반 일반학과", null, request(null),
            common(EducationBackground.GED, "98", 0, 0)))
            .isInstanceOfSatisfying(CustomException.class, exception -> {
                assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INVALID_APPLICATION_SCORE_INPUT);
                assertThat(exception.getDetail()).contains("필수 6개 과목");
            });
    }

    @Test
    void disablesKbuBonusForSusiBalanceOpportunityHealthTrack() {
        EvaluationRule rule = rule("KBOK", "경복대학교", 2026, "1",
            scores(100, 87.5, 75, 62.5, 50, 37.5, 25, 12.5, 0));
        var result = calculator.calculate(rule, "수시 기회균형 간호학과", verification("87.5"), request(null),
            common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 0));

        assertThat(result.additionalScore()).isEqualByComparingTo("0.00");
        assertThat(result.maximumTotalScore()).isEqualByComparingTo("100.00");
        assertThatThrownBy(() -> calculator.calculate(rule, "수시 기회균형 간호학과", verification("87.5"),
            request("1"), common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 0)))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INVALID_APPLICATION_SCORE_INPUT));
    }

    @Test
    void keepsKbuInterviewComponentPendingAndReportsQuantitativeMaximum() {
        EvaluationRule rule = rule("KBOK", "경복대학교", 2026, "0.4",
            scores(100, 87.5, 75, 62.5, 50, 37.5, 25, 12.5, 0));

        var result = calculator.calculate(rule, "수시 일반 항공서비스과", verification("87.5"), request("8"),
            common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 0));

        assertThat(result.academicScore()).isEqualByComparingTo("35.00");
        assertThat(result.maximumQuantitativeScore()).isEqualByComparingTo("50.00");
        assertThat(result.maximumTotalScore()).isEqualByComparingTo("110.00");
        assertThat(result.status()).isEqualTo(ApplicationScoreStatus.QUALITATIVE_PENDING);
        assertThat(result.pendingComponents()).containsExactly("면접 정성평가 60점");
        assertThat(result.finalScore()).isNull();
    }

    @Test
    void appliesSyuAthleticTalentAcademicAndAttendanceWithinFourHundredPoints() {
        EvaluationRule rule = rule(
            "SY", "삼육대학교", 2027, "3.6", scores(100, 100, 99, 99, 98, 90, 90, 70, 70)
        );
        when(rule.getRecruitmentUnit()).thenReturn("체육학과");

        var result = calculator.calculate(rule, "예체능인재", verification("99"), request(null),
            common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 8, 0));

        assertThat(result.academicScore()).isEqualByComparingTo("356.40");
        assertThat(result.attendanceScore()).isEqualByComparingTo("38.40");
        assertThat(result.maximumQuantitativeScore()).isEqualByComparingTo("400.00");
        assertThat(result.maximumTotalScore()).isEqualByComparingTo("1000.00");
        assertThat(result.status()).isEqualTo(ApplicationScoreStatus.QUALITATIVE_PENDING);
        assertThat(result.finalScore()).isNull();
        assertThat(result.pendingComponents()).containsExactly("1단계 수상실적 600점", "2단계 면접 200점");
        assertThat(result.calculationSteps()).filteredOn(step -> step.key().equals("SYU_ATTENDANCE_SCORE"))
            .singleElement().satisfies(step -> {
                assertThat(step.operands().get("환산결석일수")).isEqualByComparingTo("8");
                assertThat(step.operands().get("출결기본점수")).isEqualByComparingTo("96");
                assertThat(step.result()).isEqualByComparingTo("38.40");
            });
    }

    @Test
    void syuSchoolRecommendationPhysicalEducationDoesNotApplyAttendance() {
        EvaluationRule rule = rule(
            "SY", "삼육대학교", 2027, "4", scores(100, 100, 99, 99, 98, 90, 90, 70, 70)
        );
        when(rule.getRecruitmentUnit()).thenReturn("체육학과");

        var result = calculator.calculate(rule, "학교장추천", verification("99"), request(null),
            common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 8, 0));

        assertThat(result.academicScore()).isEqualByComparingTo("396.00");
        assertThat(result.attendanceScore()).isNull();
        assertThat(result.maximumQuantitativeScore()).isEqualByComparingTo("400.00");
        assertThat(result.pendingComponents()).containsExactly("실기고사 600점");
    }

    @Test
    void syuRuralTrackUsesPointDeductionInsteadOfSchoolRecommendationIneligibility() {
        EvaluationRule rule = rule(
            "SY", "삼육대학교", 2027, "10", scores(100, 100, 99, 99, 98, 90, 90, 70, 70)
        );
        when(rule.getRecruitmentUnit()).thenReturn("일반학과(부)");

        var result = calculator.calculate(rule, "농어촌", verification("99"), request(null),
            common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 4));

        assertThat(result.status()).isEqualTo(ApplicationScoreStatus.COMPLETE);
        assertThat(result.schoolViolenceDeduction()).isEqualByComparingTo("10.00");
        assertThat(result.finalScore()).isEqualByComparingTo("980.00");
    }

    private EvaluationRule rule(String code, String name, int year, String multiplier, Map<Integer, BigDecimal> scores) {
        EvaluationRule rule = mock(EvaluationRule.class);
        University university = mock(University.class);
        when(university.getCode()).thenReturn(code);
        when(university.getName()).thenReturn(name);
        when(rule.getUniversity()).thenReturn(university);
        when(rule.getAdmissionYear()).thenReturn(year);
        when(rule.getScoreMultiplier()).thenReturn(new BigDecimal(multiplier));
        when(rule.getGradeScores()).thenReturn(scores);
        when(rule.getAdmissionType()).thenReturn("");
        when(rule.getRecruitmentUnit()).thenReturn("");
        return rule;
    }

    private GradeVerificationResponse verification(String baseScore) {
        GradeVerificationResponse response = mock(GradeVerificationResponse.class);
        when(response.baseScore()).thenReturn(new BigDecimal(baseScore));
        return response;
    }

    private CalculateApplicationScoreRequest request(String bonus) {
        return new CalculateApplicationScoreRequest(null, null, bonus == null ? null : new BigDecimal(bonus));
    }

    private StudentCommonEvaluationSnapshot common(
        EducationBackground background,
        String gedAverage,
        int absences,
        int action
    ) {
        return new StudentCommonEvaluationSnapshot(background, GraduationStatus.EXPECTED_GRADUATE,
            gedAverage == null ? null : new BigDecimal(gedAverage),
            List.of(new StudentCommonEvaluationSnapshot.Attendance(1, absences, 0, 0, 0)),
            action == 0 ? List.of() : List.of(
                new StudentCommonEvaluationSnapshot.SchoolViolenceAction(1, action, null, true, null)));
    }

    private Map<Integer, BigDecimal> scores(double... values) {
        java.util.LinkedHashMap<Integer, BigDecimal> result = new java.util.LinkedHashMap<>();
        for (int index = 0; index < values.length; index++) {
            result.put(index + 1, BigDecimal.valueOf(values[index]));
        }
        return result;
    }

    private StudentCommonEvaluationSnapshot.GedSubjectScore ged(
        GedSubjectType type, String name, String score
    ) {
        return new StudentCommonEvaluationSnapshot.GedSubjectScore(type, name, new BigDecimal(score));
    }
}
