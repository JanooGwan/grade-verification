package com.jinhakapply.gradevalidation.admission.domain;

import java.math.BigDecimal;
import java.util.List;

public record ApplicationScoreResult(
    ApplicationScoreStatus status,
    BigDecimal academicBaseScore,
    BigDecimal academicScore,
    Integer equivalentAbsenceDays,
    BigDecimal attendanceScore,
    BigDecimal additionalScore,
    BigDecimal schoolViolenceDeduction,
    BigDecimal quantitativeSubtotal,
    BigDecimal scoreAfterDeduction,
    BigDecimal finalScore,
    BigDecimal maximumQuantitativeScore,
    BigDecimal maximumTotalScore,
    List<String> pendingComponents,
    List<String> ineligibilityReasons,
    List<String> warnings
) {
}
