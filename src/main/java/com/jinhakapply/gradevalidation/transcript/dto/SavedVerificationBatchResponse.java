package com.jinhakapply.gradevalidation.transcript.dto;

import java.time.LocalDateTime;

public record SavedVerificationBatchResponse(
    Long sourceImportId,
    Long universityId,
    String universityName,
    int admissionYear,
    String originalFileName,
    String sourceFormat,
    long resultCount,
    LocalDateTime savedAt
) {}
