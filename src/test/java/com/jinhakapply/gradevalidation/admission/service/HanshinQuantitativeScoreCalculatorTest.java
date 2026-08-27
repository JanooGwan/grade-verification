package com.jinhakapply.gradevalidation.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreStatus;
import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreResult;
import com.jinhakapply.gradevalidation.admission.domain.StudentCommonEvaluationSnapshot;
import com.jinhakapply.gradevalidation.admission.dto.CalculateApplicationScoreRequest;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.global.code.ApiResponseCode;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HanshinQuantitativeScoreCalculatorTest {
    private final HanshinQuantitativeScoreCalculator calculator =
        new HanshinQuantitativeScoreCalculator();

    @Test
    void convertsGedAverageAfterTruncatingToOneDecimal() {
        var upper = calculator.calculate("한신대학교", 2027, "학생부교과(학생부우수자)", null,
            request(null, null), common(EducationBackground.GED, "95.19", 0, 0, 0, 0, 0));
        var lower = calculator.calculate("한신대학교", 2027, "학생부교과(학생부우수자)", null,
            request(null, null), common(EducationBackground.GED, "95.09", 0, 0, 0, 0, 0));

        assertThat(upper.academicBaseScore()).isEqualByComparingTo("98.00");
        assertThat(upper.finalScore()).isEqualByComparingTo("980.00");
        assertThat(lower.academicBaseScore()).isEqualByComparingTo("97.00");
        assertThat(lower.finalScore()).isEqualByComparingTo("970.00");
    }

    @Test
    void appliesForeignHighSchoolFixedGradeFiveForNonEssayTrack() {
        var result = calculator.calculate("한신대학교", 2027, "학생부교과(고른기회)", null,
            request(null, null), common(EducationBackground.FOREIGN_HIGH_SCHOOL, null, 0, 0, 0, 0, 0));

        assertThat(result.academicBaseScore()).isEqualByComparingTo("96.00");
        assertThat(result.finalScore()).isEqualByComparingTo("960.00");
    }

    @Test
    void appliesYearSpecificPhysicalEducationRatios() {
        var commonData = common(
            EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 0, 0, 0, 0
        );

        var result2026 = calculator.calculate(
            "한신대학교", 2026, "체육실기", new BigDecimal("98"),
            request(null, "400"), commonData
        );
        var result2027 = calculator.calculate(
            "한신대학교", 2027, "체육실기", new BigDecimal("98"),
            request(null, "550"), commonData
        );

        assertThat(result2026.academicScore()).isEqualByComparingTo("588.00");
        assertThat(result2026.additionalScore()).isEqualByComparingTo("400.00");
        assertThat(result2026.finalScore()).isEqualByComparingTo("988.00");
        assertThat(result2027.academicScore()).isEqualByComparingTo("441.00");
        assertThat(result2027.additionalScore()).isEqualByComparingTo("550.00");
        assertThat(result2027.finalScore()).isEqualByComparingTo("991.00");
    }

    @ParameterizedTest
    @CsvSource({
        "2026, 401",
        "2027, 551",
        "2027, -1"
    })
    void rejectsPhysicalEducationScoresOutsideYearSpecificRange(int admissionYear, String practicalScore) {
        assertThatThrownBy(() -> calculator.calculate(
            "한신대학교", admissionYear, "체육실기", new BigDecimal("98"),
            request(null, practicalScore),
            common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 0, 0, 0, 0)
        )).isInstanceOfSatisfying(CustomException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INVALID_APPLICATION_SCORE_INPUT));
    }

    @Test
    void convertsForeignEssayScoreToSubstituteAcademicScoreAndAddsEssayScore() {
        var result = calculator.calculate("한신대학교", 2027, "논술전형", null,
            request("794", null), common(EducationBackground.FOREIGN_HIGH_SCHOOL, null, 0, 0, 0, 0, 0));

        assertThat(result.academicBaseScore()).isEqualByComparingTo("99.00");
        assertThat(result.academicScore()).isEqualByComparingTo("198.00");
        assertThat(result.additionalScore()).isEqualByComparingTo("794.00");
        assertThat(result.finalScore()).isEqualByComparingTo("992.00");
    }

    @Test
    void calculatesAttendanceButKeepsTalentTrackPendingForInterview() {
        var result = calculator.calculate("한신대학교", 2027, "학생부교과(참인재)", new BigDecimal("98.214"),
            request(null, null), common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 4, 2, 1, 0, 0));

        assertThat(result.equivalentAbsenceDays()).isEqualTo(5);
        assertThat(result.attendanceScore()).isEqualByComparingTo("58.00");
        assertThat(result.academicScore()).isEqualByComparingTo("530.36");
        assertThat(result.quantitativeSubtotal()).isEqualByComparingTo("588.36");
        assertThat(result.status()).isEqualTo(ApplicationScoreStatus.QUALITATIVE_PENDING);
        assertThat(result.finalScore()).isNull();
        assertThat(result.pendingComponents()).containsExactly("면접 정성평가 400점");
    }

    @Test
    void appliesSchoolViolenceDeductionToQuantitativeTotal() {
        var result = calculator.calculate("한신대학교", 2027, "학생부교과(학생부우수자)", new BigDecimal("98"),
            request(null, null), common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 0, 0, 0, 8));

        assertThat(result.schoolViolenceDeduction()).isEqualByComparingTo("20.00");
        assertThat(result.finalScore()).isEqualByComparingTo("960.00");
    }

    @Test
    void marksSchoolRecommendationApplicantWithViolenceRecordIneligible() {
        var result = calculator.calculate("한신대학교", 2027, "학생부교과(학교장추천)", new BigDecimal("98"),
            request(null, null), common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 0, 0, 0, 4));

        assertThat(result.status()).isEqualTo(ApplicationScoreStatus.INELIGIBLE);
        assertThat(result.finalScore()).isNull();
        assertThat(result.ineligibilityReasons()).singleElement()
            .asString().contains("추천 대상에서 제외");
    }

    @Test
    void usesHighestActiveActionWhenMultipleSchoolViolenceActionsExist() {
        var commonData = new StudentCommonEvaluationSnapshot(
            EducationBackground.DOMESTIC_HIGH_SCHOOL, GraduationStatus.GRADUATE, null, List.of(),
            List.of(
                new StudentCommonEvaluationSnapshot.SchoolViolenceAction(1, 4, null, true, null),
                new StudentCommonEvaluationSnapshot.SchoolViolenceAction(2, 8, null, true, null),
                new StudentCommonEvaluationSnapshot.SchoolViolenceAction(3, 9, null, false, null)
            )
        );

        var result = calculator.calculate("한신대학교", 2027, "학생부교과(학생부우수자)",
            new BigDecimal("98"), request(null, null), commonData);

        assertThat(result.schoolViolenceDeduction()).isEqualByComparingTo("20.00");
        assertThat(result.warnings()).contains("복수의 학교폭력 조치 중 가장 높은 조치 호수를 적용했습니다.");
    }

    @Test
    void requiresAttendanceForDomesticTalentTrack() {
        var commonData = new StudentCommonEvaluationSnapshot(
            EducationBackground.DOMESTIC_HIGH_SCHOOL, GraduationStatus.EXPECTED_GRADUATE,
            null, List.of(), List.of()
        );

        assertThatThrownBy(() -> calculator.calculate(
            "\ud55c\uc2e0\ub300\ud559\uad50", 2027, "\ud559\uc0dd\ubd80\uc885\ud569(\ucc38\uc778\uc7ac)",
            new BigDecimal("98"), request(null, null), commonData
        )).isInstanceOfSatisfying(CustomException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INVALID_APPLICATION_SCORE_INPUT));
    }

    @Test
    void ignoresInactiveSchoolViolenceAction() {
        var commonData = new StudentCommonEvaluationSnapshot(
            EducationBackground.DOMESTIC_HIGH_SCHOOL, GraduationStatus.GRADUATE, null, List.of(),
            List.of(new StudentCommonEvaluationSnapshot.SchoolViolenceAction(3, 9, null, false, null))
        );

        var result = calculator.calculate(
            "\ud55c\uc2e0\ub300\ud559\uad50", 2027, "\uc77c\ubc18\uc804\ud615", new BigDecimal("98"),
            request(null, null), commonData
        );

        assertThat(result.schoolViolenceDeduction()).isEqualByComparingTo("0.00");
        assertThat(result.finalScore()).isEqualByComparingTo("980.00");
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0.00", "1, 0.00", "3, 0.00", "4, 3.00", "5, 3.00",
        "6, 5.00", "7, 5.00", "8, 20.00", "9, 20.00"
    })
    void appliesSchoolViolenceDeductionBoundaries(int actionNumber, String expectedDeduction) {
        var result = calculator.calculate(
            "\ud55c\uc2e0\ub300\ud559\uad50", 2027, "\uc77c\ubc18\uc804\ud615", new BigDecimal("98"),
            request(null, null),
            common(EducationBackground.DOMESTIC_HIGH_SCHOOL, null, 0, 0, 0, 0, actionNumber)
        );

        assertThat(result.schoolViolenceDeduction()).isEqualByComparingTo(expectedDeduction);
    }

    private CalculateApplicationScoreRequest request(String essayScore, String practicalScore) {
        return new CalculateApplicationScoreRequest(decimal(essayScore), decimal(practicalScore));
    }

    private StudentCommonEvaluationSnapshot common(
        EducationBackground background,
        String gedAverage,
        int absences,
        int tardy,
        int earlyLeave,
        int classAbsence,
        int violenceAction
    ) {
        return new StudentCommonEvaluationSnapshot(
            background, GraduationStatus.EXPECTED_GRADUATE, decimal(gedAverage),
            List.of(new StudentCommonEvaluationSnapshot.Attendance(1, absences, tardy, earlyLeave, classAbsence)),
            violenceAction == 0 ? List.of() : List.of(
                new StudentCommonEvaluationSnapshot.SchoolViolenceAction(1, violenceAction, null, true, null)
            )
        );
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
