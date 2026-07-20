package com.jinhakapply.gradevalidation.admission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAdmissionTrackRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull Boolean active
) {}
