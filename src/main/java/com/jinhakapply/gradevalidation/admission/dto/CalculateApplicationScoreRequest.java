package com.jinhakapply.gradevalidation.admission.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record CalculateApplicationScoreRequest(
    @DecimalMin("0.0") @DecimalMax("800.0") BigDecimal essayScore,
    @DecimalMin("0.0") @DecimalMax("550.0") BigDecimal practicalScore
) {
}
