package com.jinhakapply.gradevalidation.transcript.dto;

import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;

public record TranscriptImportResponse(
    Long importId,
    TranscriptImportStatus status,
    String sourceFormat,
    int totalRows,
    int importedRows,
    int failedRows,
    int skippedRows,
    int createdStudents,
    int updatedStudents,
    int createdCourses,
    int updatedCourses,
    int deletedCourses,
    int applicationRows,
    int createdApplications,
    int deletedApplications,
    int createdAdmissionTracks,
    int createdRecruitmentUnits,
    List<TranscriptImportRowError> errors,
    List<String> warnings
) {
}
