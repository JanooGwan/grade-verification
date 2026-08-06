package com.jinhakapply.gradevalidation.transcript.service;

import java.util.List;

import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportRowError;

record TranscriptExcelParseResult(
    int totalRows,
    List<TranscriptExcelRow> rows,
    List<TranscriptImportRowError> errors,
    List<TranscriptImportRowError> skipped
) {
    TranscriptExcelParseResult(
        int totalRows,
        List<TranscriptExcelRow> rows,
        List<TranscriptImportRowError> errors
    ) {
        this(totalRows, rows, errors, List.of());
    }
}
