package com.jinhakapply.gradevalidation.admission.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreStatus;
import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreResult;
import com.jinhakapply.gradevalidation.admission.domain.StudentCommonEvaluationSnapshot;
import com.jinhakapply.gradevalidation.admission.dto.CalculateApplicationScoreRequest;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import org.junit.jupiter.api.Test;

class Hanshin2027QuantitativeScoreCalculatorTest {
    private final Hanshin2027QuantitativeScoreCalculator calculator =
        new Hanshin2027QuantitativeScoreCalculator();

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
