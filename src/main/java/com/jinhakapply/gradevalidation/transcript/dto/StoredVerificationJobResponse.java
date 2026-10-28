package com.jinhakapply.gradevalidation.transcript.dto;

import java.time.Instant;
import java.util.UUID;

public record StoredVerificationJobResponse(
    UUID jobId,
    Long universityId,
    int admissionYear,
    String status,
    String message,
    Instant startedAt,
    Instant completedAt,
    StoredVerificationPersistenceResponse result,
    StoredVerificationProgress progress
) {
    public StoredVerificationJobResponse(UUID jobId, Long universityId, int admissionYear, String status,
        String message, Instant startedAt, Instant completedAt, StoredVerificationPersistenceResponse result) {
        this(jobId, universityId, admissionYear, status, message, startedAt, completedAt, result, null);
    }
}
