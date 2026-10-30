package com.jinhakapply.gradevalidation.operation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.jinhakapply.gradevalidation.transcript.dto.StoredVerificationJobResponse;
import com.jinhakapply.gradevalidation.transcript.service.SourceImportJobTracker.ActiveImport;

public record ActiveJobsResponse(List<StoredVerificationJobResponse> verifications, List<ActiveExport> exports,
    List<ActiveImport> imports) {
    public record ActiveExport(UUID exportId, Long sourceImportId, Long universityId, Integer admissionYear, Instant startedAt) {}
}
