package com.jinhakapply.gradevalidation.transcript.service;

import java.util.List;

import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportRowError;

record TransferExcelParseResult(
    String sourceFormat,
    List<TransferApplicationRow> applications,
    List<TranscriptExcelRow> courses,
    int invalidRows,
    int skippedRows,
    List<TranscriptImportRowError> errors,
    List<String> warnings
) {
    int totalRows() {
        return courses.size() + invalidRows + skippedRows;
    }
}
