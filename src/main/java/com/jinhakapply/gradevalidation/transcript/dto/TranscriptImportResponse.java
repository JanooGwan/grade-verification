package com.jinhakapply.gradevalidation.transcript.dto;

import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;

public record TranscriptImportResponse(
    Long importId,
    TranscriptImportStatus status,
    int totalRows,
    int importedRows,
    int failedRows,
    int createdStudents,
    int updatedStudents,
    int createdCourses,
    int updatedCourses,
    List<TranscriptImportRowError> errors
) {
}
