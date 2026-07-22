package com.jinhakapply.gradevalidation.admission.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.GedSubjectType;
import com.jinhakapply.gradevalidation.transcript.domain.LegacySummaryType;

public record StudentCommonEvaluationSnapshot(
    EducationBackground educationBackground,
    HighSchoolType highSchoolType,
    GraduationStatus graduationStatus,
    Integer graduationYear,
    BigDecimal gedAverageScore,
    List<GedSubjectScore> gedSubjectScores,
    List<LegacyGradeSummary> legacyGradeSummaries,
    List<Attendance> attendance,
    List<SchoolViolenceAction> schoolViolenceActions
) {
    public StudentCommonEvaluationSnapshot(
        EducationBackground educationBackground,
        HighSchoolType highSchoolType,
        GraduationStatus graduationStatus,
        BigDecimal gedAverageScore,
        List<Attendance> attendance,
        List<SchoolViolenceAction> schoolViolenceActions
    ) {
        this(educationBackground, highSchoolType, graduationStatus, null, gedAverageScore,
            List.of(), List.of(), attendance, schoolViolenceActions);
    }

    public StudentCommonEvaluationSnapshot(
        EducationBackground educationBackground,
        GraduationStatus graduationStatus,
        BigDecimal gedAverageScore,
        List<Attendance> attendance,
        List<SchoolViolenceAction> schoolViolenceActions
    ) {
        this(educationBackground, HighSchoolType.GENERAL, graduationStatus, null, gedAverageScore,
            List.of(), List.of(), attendance, schoolViolenceActions);
    }

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

    public record GedSubjectScore(GedSubjectType subjectType, String subjectName, BigDecimal score) {}

    public record LegacyGradeSummary(LegacySummaryType summaryType, int schoolYear, Integer semester,
        int rankPosition, Integer tiedRankCount, int cohortSize, BigDecimal credits) {}

    public record SchoolViolenceAction(
        Integer schoolYear,
        int actionNumber,
        LocalDate actionDate,
        boolean active,
        String note
    ) {}
}
