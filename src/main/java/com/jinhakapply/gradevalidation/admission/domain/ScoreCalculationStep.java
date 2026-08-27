package com.jinhakapply.gradevalidation.admission.domain;

import java.math.BigDecimal;
import java.util.Map;

public record ScoreCalculationStep(
    String key,
    String description,
    String formula,
    Map<String, BigDecimal> operands,
    BigDecimal result
) {
    public ScoreCalculationStep {
        operands = Map.copyOf(operands);
    }
}
