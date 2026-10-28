package com.jinhakapply.gradevalidation.transcript.dto;

/** Aggregate counts only; savedResults is provisional until the job completes its transaction. */
public record StoredVerificationProgress(String stage, int totalApplications, int savedResults, int failedResults) {
    public static StoredVerificationProgress queued() {
        return new StoredVerificationProgress("QUEUED", 0, 0, 0);
    }
}
