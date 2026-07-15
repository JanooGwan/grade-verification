package com.jinhakapply.gradevalidation.transcript.dto;

import java.time.LocalDateTime;

import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportMode;

public record TranscriptImportSummaryResponse(
    Long importId,
    int admissionYear,
    String originalFileName,
    TranscriptImportMode importMode,
    String fileSha256,
    int totalRows,
    int importedRows,
    int failedRows,
    TranscriptImportStatus status,
    LocalDateTime createdAt
) {
    public static TranscriptImportSummaryResponse from(StudentTranscriptImport transcriptImport) {
        return new TranscriptImportSummaryResponse(
            transcriptImport.getId(),
            transcriptImport.getAdmissionYear(),
            transcriptImport.getOriginalFileName(),
            transcriptImport.getImportMode(),
            transcriptImport.getFileSha256(),
            transcriptImport.getTotalRows(),
            transcriptImport.getImportedRows(),
            transcriptImport.getFailedRows(),
            transcriptImport.getStatus(),
            transcriptImport.getCreatedAt()
        );
    }
}
