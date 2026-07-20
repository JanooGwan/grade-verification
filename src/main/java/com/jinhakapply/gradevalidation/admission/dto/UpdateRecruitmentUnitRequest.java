package com.jinhakapply.gradevalidation.admission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRecruitmentUnitRequest(
    @Size(max = 30) String code,
    @NotBlank @Size(max = 120) String name,
    @NotNull Boolean active
) {}
