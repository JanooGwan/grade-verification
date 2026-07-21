package com.jinhakapply.gradevalidation.transcript.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateStudentCommonDataRequest(
    @NotNull EducationBackground educationBackground,
    @NotNull GraduationStatus graduationStatus,
    @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal gedAverageScore,
    @NotNull @Size(max = 3) List<@Valid Attendance> attendance,
    @NotNull @Size(max = 20) List<@Valid SchoolViolenceAction> schoolViolenceActions
) {
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
