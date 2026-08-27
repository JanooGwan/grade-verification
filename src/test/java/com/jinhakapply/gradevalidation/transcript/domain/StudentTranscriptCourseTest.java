package com.jinhakapply.gradevalidation.transcript.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import org.junit.jupiter.api.Test;

class StudentTranscriptCourseTest {

    @Test
    void updatesAllScoreAndSourceFields() {
        Student student = Student.create(2027, "A-001", "학생", null, null, 2027);
        StudentTranscriptCourse course = StudentTranscriptCourse.create(
            student, 1, 1, SubjectCategory.MATH, "수학"
        );

        course.updateScore(
            2, AchievementLevel.A, new BigDecimal("95"), new BigDecimal("70"),
            new BigDecimal("12.5"), 200, new BigDecimal("3"), true, false,
            "transcript.xlsx", 5
        );

        assertThat(course.getGrade()).isEqualTo(2);
        assertThat(course.getAchievement()).isEqualTo(AchievementLevel.A);
        assertThat(course.getRawScore()).isEqualByComparingTo("95");
        assertThat(course.getMeanScore()).isEqualByComparingTo("70");
        assertThat(course.getStandardDeviation()).isEqualByComparingTo("12.5");
        assertThat(course.getStudentCount()).isEqualTo(200);
        assertThat(course.getCredits()).isEqualByComparingTo("3");
        assertThat(course.isCareerSubject()).isTrue();
        assertThat(course.isProfessionalCourse()).isFalse();
        assertThat(course.getSourceFileName()).isEqualTo("transcript.xlsx");
        assertThat(course.getSourceRowNumber()).isEqualTo(5);
    }

    @Test
    void manualCourseUpdateAlsoChangesIdentityFields() {
        Student student = Student.create(2027, "A-001", "학생", null, null, 2027);
        StudentTranscriptCourse course = StudentTranscriptCourse.create(
            student, 1, 1, SubjectCategory.MATH, "수학"
        );

        course.updateCourse(
            2, 2, SubjectCategory.SCIENCE, "물리학", 3, null,
            null, null, null, null, new BigDecimal("2"), false, true,
            "MANUAL", 0
        );

        assertThat(course.getSchoolYear()).isEqualTo(2);
        assertThat(course.getSemester()).isEqualTo(2);
        assertThat(course.getSubjectCategory()).isEqualTo(SubjectCategory.SCIENCE);
        assertThat(course.getCourseName()).isEqualTo("물리학");
        assertThat(course.getGrade()).isEqualTo(3);
        assertThat(course.isProfessionalCourse()).isTrue();
    }
}
