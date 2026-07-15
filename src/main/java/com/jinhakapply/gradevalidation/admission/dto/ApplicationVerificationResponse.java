package com.jinhakapply.gradevalidation.admission.dto;

import java.time.LocalDateTime;

import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;

public record ApplicationVerificationResponse(
    Long verificationRunId,
    LocalDateTime createdAt,
    StudentApplicationResponse application,
    GradeVerificationResponse verification
) {}
