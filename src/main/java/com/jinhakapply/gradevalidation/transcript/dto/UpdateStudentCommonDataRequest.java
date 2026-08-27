package com.jinhakapply.gradevalidation.transcript.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.GedSubjectType;
import com.jinhakapply.gradevalidation.transcript.domain.LegacySummaryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateStudentCommonDataRequest(
    @NotNull EducationBackground educationBackground,
    @NotNull HighSchoolType highSchoolType,
    @NotNull GraduationStatus graduationStatus,
    @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal gedAverageScore,
    @Size(max = 20) List<@Valid GedSubjectScore> gedSubjectScores,
    @Size(max = 6) List<@Valid LegacyGradeSummary> legacyGradeSummaries,
    @NotNull @Size(max = 3) List<@Valid Attendance> attendance,
    @NotNull @Size(max = 20) List<@Valid SchoolViolenceAction> schoolViolenceActions
) {
    public UpdateStudentCommonDataRequest {
        gedSubjectScores = gedSubjectScores == null ? List.of() : List.copyOf(gedSubjectScores);
        legacyGradeSummaries = legacyGradeSummaries == null ? List.of() : List.copyOf(legacyGradeSummaries);
    }

    public UpdateStudentCommonDataRequest(
        EducationBackground educationBackground,
        GraduationStatus graduationStatus,
        BigDecimal gedAverageScore,
        List<Attendance> attendance,
        List<SchoolViolenceAction> schoolViolenceActions
    ) {
        this(educationBackground, HighSchoolType.GENERAL, graduationStatus, gedAverageScore, List.of(), List.of(),
            attendance, schoolViolenceActions);
    }

    public UpdateStudentCommonDataRequest(
        EducationBackground educationBackground,
        HighSchoolType highSchoolType,
        GraduationStatus graduationStatus,
        BigDecimal gedAverageScore,
        List<Attendance> attendance,
        List<SchoolViolenceAction> schoolViolenceActions
    ) {
        this(educationBackground, highSchoolType, graduationStatus, gedAverageScore, List.of(), List.of(),
            attendance, schoolViolenceActions);
    }

    public record GedSubjectScore(
        @NotNull GedSubjectType subjectType,
        @NotNull @Size(min = 1, max = 100) String subjectName,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal score
    ) {}

    public record LegacyGradeSummary(
        @NotNull LegacySummaryType summaryType,
        @Min(1) @Max(3) int schoolYear,
        @Min(1) @Max(2) Integer semester,
        @Min(1) int rankPosition,
        @Min(1) Integer tiedRankCount,
        @Min(1) int cohortSize,
        @NotNull @DecimalMin("0.01") BigDecimal credits
    ) {}

    public record Attendance(
        @Min(1) @Max(3) int schoolYear,
        @Min(0) int unexcusedAbsenceDays,
        @Min(0) int unexcusedTardyCount,
        @Min(0) int unexcusedEarlyLeaveCount,
        @Min(0) int unexcusedClassAbsenceCount
    ) {}

    public record SchoolViolenceAction(
        @Min(1) @Max(3) Integer schoolYear,
        @Min(1) @Max(9) int actionNumber,
        LocalDate actionDate,
        boolean active,
        @Size(max = 500) String note
    ) {}
}
