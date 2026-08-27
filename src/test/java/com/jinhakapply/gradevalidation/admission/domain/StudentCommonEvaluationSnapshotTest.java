package com.jinhakapply.gradevalidation.admission.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import org.junit.jupiter.api.Test;

class StudentCommonEvaluationSnapshotTest {

    @Test
    void aggregatesAttendanceAcrossSchoolYearsAndUsesHighestActiveViolenceAction() {
        var snapshot = new StudentCommonEvaluationSnapshot(
            EducationBackground.DOMESTIC_HIGH_SCHOOL,
            GraduationStatus.GRADUATE,
            null,
            List.of(
                new StudentCommonEvaluationSnapshot.Attendance(1, 1, 2, 3, 4),
                new StudentCommonEvaluationSnapshot.Attendance(2, 5, 6, 7, 8),
                new StudentCommonEvaluationSnapshot.Attendance(3, 9, 10, 11, 12)
            ),
            List.of(
                new StudentCommonEvaluationSnapshot.SchoolViolenceAction(1, 4, null, true, null),
                new StudentCommonEvaluationSnapshot.SchoolViolenceAction(2, 7, null, true, null),
                new StudentCommonEvaluationSnapshot.SchoolViolenceAction(3, 9, null, false, null)
            )
        );

        assertThat(snapshot.totalUnexcusedAbsenceDays()).isEqualTo(15);
        assertThat(snapshot.totalUnexcusedTardyCount()).isEqualTo(18);
        assertThat(snapshot.totalUnexcusedEarlyLeaveCount()).isEqualTo(21);
        assertThat(snapshot.totalUnexcusedClassAbsenceCount()).isEqualTo(24);
        assertThat(snapshot.highestActiveSchoolViolenceAction()).isEqualTo(7);
    }
}
