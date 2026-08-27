package com.jinhakapply.gradevalidation.transcript.dto;

import java.time.LocalDateTime;

import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportMode;

public record TranscriptImportSummaryResponse(
    Long importId,
    Long universityId,
    String universityName,
    int admissionYear,
    String originalFileName,
    TranscriptImportMode importMode,
    String fileSha256,
    int totalRows,
    int importedRows,
    int failedRows,
    TranscriptImportStatus status,
    String sourceFormat,
    String errorMessage,
    LocalDateTime createdAt
) {
    public static TranscriptImportSummaryResponse from(StudentTranscriptImport transcriptImport) {
        return new TranscriptImportSummaryResponse(
            transcriptImport.getId(),
            transcriptImport.getUniversity().getId(),
            transcriptImport.getUniversity().getName(),
            transcriptImport.getAdmissionYear(),
            transcriptImport.getOriginalFileName(),
            transcriptImport.getImportMode(),
            transcriptImport.getFileSha256(),
            transcriptImport.getTotalRows(),
            transcriptImport.getImportedRows(),
            transcriptImport.getFailedRows(),
            transcriptImport.getStatus(),
            transcriptImport.getSourceFormat(),
            transcriptImport.getErrorMessage(),
            transcriptImport.getCreatedAt()
        );
    }
}
