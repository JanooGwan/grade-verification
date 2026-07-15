package com.jinhakapply.gradevalidation.evaluation.dto;

import java.util.List;

public record RuleExtractionComparisonResponse(
    RuleExtractionResponse left,
    RuleExtractionResponse right,
    List<FieldDifference> differences
) {
    public record FieldDifference(String field, String leftValue, String rightValue) {}
}
