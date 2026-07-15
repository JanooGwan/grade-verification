package com.jinhakapply.gradevalidation.transcript.dto;

public record TranscriptImportRowError(
    int rowNumber,
    String reason
) {
}
