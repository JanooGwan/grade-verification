package com.jinhakapply.gradevalidation.transcript.service;

import java.util.List;

import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportRowError;

record TransferExcelParseResult(
    String sourceFormat,
    List<TransferApplicationRow> applications,
    List<TranscriptExcelRow> courses,
    int invalidRows,
    int skippedRows,
    List<TranscriptImportRowError> skipped,
    List<TranscriptImportRowError> errors,
    List<String> warnings,
    java.util.Map<String, TukApplicantProfile> applicantProfiles
) {
    TransferExcelParseResult(String sourceFormat, List<TransferApplicationRow> applications,
        List<TranscriptExcelRow> courses, int invalidRows, int skippedRows,
        List<TranscriptImportRowError> skipped, List<TranscriptImportRowError> errors, List<String> warnings) {
        this(sourceFormat, applications, courses, invalidRows, skippedRows, skipped, errors, warnings, java.util.Map.of());
    }
    int totalRows() {
        return courses.size() + invalidRows + skippedRows;
    }
}
