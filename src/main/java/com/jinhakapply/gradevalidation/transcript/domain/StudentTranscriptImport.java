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

    @Enumerated(STRING)
    @Column(nullable = false, length = 30)
    private TranscriptImportStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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
        this.createdAt = LocalDateTime.now();
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
}
