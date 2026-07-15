package com.jinhakapply.gradevalidation.admission.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAdmissionTrackRequest(
    @NotNull Long universityId,
    @Min(2000) @Max(2100) int admissionYear,
    @NotBlank @Size(max = 100) String name
) {}
