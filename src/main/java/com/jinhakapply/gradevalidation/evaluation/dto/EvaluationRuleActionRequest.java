package com.jinhakapply.gradevalidation.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EvaluationRuleActionRequest(
    @NotBlank @Size(max = 100) String actor,
    @Size(max = 1000) String note
) {
}
