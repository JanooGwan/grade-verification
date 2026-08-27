package com.jinhakapply.gradevalidation.transcript.dto;

import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;

public record SourceImportStartResponse(
    Long importId,
    TranscriptImportStatus status,
    String sourceFormat,
    String message
) {}
