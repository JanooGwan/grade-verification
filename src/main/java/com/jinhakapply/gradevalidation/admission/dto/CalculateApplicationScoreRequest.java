package com.jinhakapply.gradevalidation.admission.dto;

import java.math.BigDecimal;

import com.jinhakapply.gradevalidation.admission.domain.EducationBackground;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CalculateApplicationScoreRequest(
    @NotNull EducationBackground educationBackground,
    @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal gedAverageScore,
    @Min(0) Integer unexcusedAbsenceDays,
    @Min(0) Integer unexcusedTardyCount,
    @Min(0) Integer unexcusedEarlyLeaveCount,
    @Min(0) Integer unexcusedClassAbsenceCount,
    @Min(0) @Max(9) Integer schoolViolenceAction,
    @DecimalMin("0.0") @DecimalMax("800.0") BigDecimal essayScore,
    @DecimalMin("0.0") @DecimalMax("550.0") BigDecimal practicalScore
) {
}
