package com.jinhakapply.gradevalidation.transcript.dto;

import java.time.LocalDateTime;

public record StoredVerificationPersistenceResponse(
    Long sourceImportId,
    int totalApplications,
    int savedResults,
    int failedResults,
    int replacedResults,
    LocalDateTime savedAt
) {}
