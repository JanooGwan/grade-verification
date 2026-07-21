package com.jinhakapply.gradevalidation.admission.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;

public record StudentCommonEvaluationSnapshot(
    EducationBackground educationBackground,
    GraduationStatus graduationStatus,
    BigDecimal gedAverageScore,
    List<Attendance> attendance,
    List<SchoolViolenceAction> schoolViolenceActions
) {
    public int totalUnexcusedAbsenceDays() {
        return attendance.stream().mapToInt(Attendance::unexcusedAbsenceDays).sum();
    }

    public int totalUnexcusedTardyCount() {
        return attendance.stream().mapToInt(Attendance::unexcusedTardyCount).sum();
    }

    public int totalUnexcusedEarlyLeaveCount() {
        return attendance.stream().mapToInt(Attendance::unexcusedEarlyLeaveCount).sum();
    }

    public int totalUnexcusedClassAbsenceCount() {
        return attendance.stream().mapToInt(Attendance::unexcusedClassAbsenceCount).sum();
    }

    public int highestActiveSchoolViolenceAction() {
        return schoolViolenceActions.stream().filter(SchoolViolenceAction::active)
            .mapToInt(SchoolViolenceAction::actionNumber).max().orElse(0);
    }

    public record Attendance(
        int schoolYear,
        int unexcusedAbsenceDays,
        int unexcusedTardyCount,
        int unexcusedEarlyLeaveCount,
        int unexcusedClassAbsenceCount
    ) {}

    public record SchoolViolenceAction(
        Integer schoolYear,
        int actionNumber,
        LocalDate actionDate,
        boolean active,
        String note
    ) {}
}
