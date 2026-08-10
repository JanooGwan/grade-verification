package com.jinhakapply.gradevalidation.transcript.dto;

import java.time.LocalDateTime;

import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;

public record SavedVerificationDetailResponse(
    Long verificationRunId,
    Long sourceImportId,
    Long studentId,
    String applicantNumber,
    String studentName,
    LocalDateTime savedAt,
    GradeVerificationResponse verification
) {}
