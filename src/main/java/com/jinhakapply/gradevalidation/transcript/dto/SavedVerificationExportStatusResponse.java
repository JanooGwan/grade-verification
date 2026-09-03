package com.jinhakapply.gradevalidation.transcript.dto;

import java.util.UUID;

public record SavedVerificationExportStatusResponse(
    UUID exportId,
    Long sourceImportId,
    String status,
    String message
) {}
