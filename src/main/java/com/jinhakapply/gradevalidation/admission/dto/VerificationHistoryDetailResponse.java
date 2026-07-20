package com.jinhakapply.gradevalidation.admission.dto;

import java.time.LocalDateTime;

import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;

public record VerificationHistoryDetailResponse(
    Long verificationRunId,
    Long studentId,
    Long applicationId,
    LocalDateTime createdAt,
    GradeVerificationResponse verification
) {}
