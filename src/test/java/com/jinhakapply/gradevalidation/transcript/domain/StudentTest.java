package com.jinhakapply.gradevalidation.transcript.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class StudentTest {

    @Test
    void infersGraduationStatusFromAdmissionAndGraduationYears() {
        Student graduate = Student.create(2027, "A-001", "졸업자", null, null, 2026);
        Student expectedGraduate = Student.create(2027, "A-002", "졸업예정자", null, null, 2027);

        assertThat(graduate.getGraduationStatus()).isEqualTo(GraduationStatus.GRADUATE);
        assertThat(expectedGraduate.getGraduationStatus()).isEqualTo(GraduationStatus.EXPECTED_GRADUATE);
    }

    @Test
    void commonEvaluationProfileCanOverrideImportedDefaults() {
        Student student = Student.create(2027, "A-001", "지원자", null, null, null);

        student.updateCommonEvaluationProfile(
            EducationBackground.GED, GraduationStatus.GRADUATE, new BigDecimal("94.50")
        );

        assertThat(student.getEducationBackground()).isEqualTo(EducationBackground.GED);
        assertThat(student.getGraduationStatus()).isEqualTo(GraduationStatus.GRADUATE);
        assertThat(student.getGedAverageScore()).isEqualByComparingTo("94.50");
    }
}
