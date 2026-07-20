package com.jinhakapply.gradevalidation.admission.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateStudentApplicationRequest(@NotNull @Positive Long recruitmentUnitId) {}
