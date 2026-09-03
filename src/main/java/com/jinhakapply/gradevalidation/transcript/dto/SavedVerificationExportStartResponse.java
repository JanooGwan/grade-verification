package com.jinhakapply.gradevalidation.transcript.dto;

import java.util.UUID;

public record SavedVerificationExportStartResponse(
    UUID exportId,
    Long sourceImportId,
    String status
) {}
