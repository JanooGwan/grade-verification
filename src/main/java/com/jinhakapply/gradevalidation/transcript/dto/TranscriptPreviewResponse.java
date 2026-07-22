package com.jinhakapply.gradevalidation.transcript.dto;

import java.math.BigDecimal;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;

public record TranscriptPreviewResponse(
    String originalFileName,
    String fileSha256,
    String sourceFormat,
    int applicationRows,
    int totalRows,
    int validRows,
    int invalidRows,
    int skippedRows,
    List<PreviewRow> sampleRows,
    List<TranscriptImportRowError> errors,
    List<String> warnings
) {
    public record PreviewRow(
        int rowNumber,
        String applicantNumber,
        String studentName,
        int schoolYear,
        int semester,
        SubjectCategory subjectCategory,
        String courseName,
        Integer grade,
        AchievementLevel achievement,
        BigDecimal credits
    ) {}
}
