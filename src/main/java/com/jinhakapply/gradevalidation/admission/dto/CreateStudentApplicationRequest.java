package com.jinhakapply.gradevalidation.admission.dto;

import jakarta.validation.constraints.NotNull;

public record CreateStudentApplicationRequest(@NotNull Long recruitmentUnitId) {}
