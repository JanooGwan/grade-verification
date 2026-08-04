package com.jinhakapply.gradevalidation.evaluation.dto;

import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy;
import jakarta.validation.constraints.NotNull;

public record ConfigureSelectionPolicyRequest(
    @NotNull CourseSelectionPolicy policy
) {}
