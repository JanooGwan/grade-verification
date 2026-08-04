package com.jinhakapply.gradevalidation.transcript.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "student_transcript_import")
@NoArgsConstructor(access = PROTECTED)
public class StudentTranscriptImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admission_year", nullable = false)
    private int admissionYear;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Enumerated(STRING)
    @Column(name = "import_mode", nullable = false, length = 30)
    private TranscriptImportMode importMode;

    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Column(name = "source_format", nullable = false, length = 50)
    private String sourceFormat;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Enumerated(STRING)
    @Column(nullable = false, length = 30)
    private TranscriptImportStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private StudentTranscriptImport(
        int admissionYear,
        String originalFileName,
        TranscriptImportMode importMode,
        String fileSha256,
        int totalRows,
        int importedRows,
        int failedRows
    ) {
        this.admissionYear = admissionYear;
        this.originalFileName = originalFileName;
        this.importMode = importMode;
        this.fileSha256 = fileSha256;
        this.totalRows = totalRows;
        this.importedRows = importedRows;
        this.failedRows = failedRows;
        this.status = failedRows == 0
            ? TranscriptImportStatus.COMPLETED
            : TranscriptImportStatus.COMPLETED_WITH_ERRORS;
        this.sourceFormat = "STANDARD_TRANSCRIPT_V1";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static StudentTranscriptImport create(
        int admissionYear,
        String originalFileName,
        TranscriptImportMode importMode,
        String fileSha256,
        int totalRows,
        int importedRows,
        int failedRows
    ) {
        return new StudentTranscriptImport(
            admissionYear,
            originalFileName,
            importMode,
            fileSha256,
            totalRows,
            importedRows,
            failedRows
        );
    }

    public static StudentTranscriptImport create(
        int admissionYear,
        String originalFileName,
        TranscriptImportMode importMode,
        String fileSha256,
        int totalRows,
        int importedRows,
        int failedRows,
        String sourceFormat
    ) {
        StudentTranscriptImport transcriptImport = create(
            admissionYear, originalFileName, importMode, fileSha256, totalRows, importedRows, failedRows
        );
        transcriptImport.sourceFormat = sourceFormat;
        return transcriptImport;
    }

    public static StudentTranscriptImport queue(
        int admissionYear,
        String originalFileName,
        String fileSha256,
        String sourceFormat
    ) {
        StudentTranscriptImport transcriptImport = new StudentTranscriptImport();
        transcriptImport.admissionYear = admissionYear;
        transcriptImport.originalFileName = originalFileName;
        transcriptImport.importMode = TranscriptImportMode.VALID_ROWS_ONLY;
        transcriptImport.fileSha256 = fileSha256;
        transcriptImport.sourceFormat = sourceFormat;
        transcriptImport.status = TranscriptImportStatus.QUEUED;
        transcriptImport.createdAt = LocalDateTime.now();
        transcriptImport.updatedAt = transcriptImport.createdAt;
        return transcriptImport;
    }
}
