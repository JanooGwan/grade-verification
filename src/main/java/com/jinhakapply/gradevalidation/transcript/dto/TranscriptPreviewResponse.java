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
    VerificationSummary verification,
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

    public record VerificationSummary(
        int totalApplications,
        int successfulApplications,
        int failedApplications,
        List<VerificationResultRow> sampleResults
    ) {}

    public record VerificationResultRow(
        int applicationRowNumber,
        String applicantNumber,
        String studentName,
        String admissionTrackName,
        String recruitmentUnitName,
        BigDecimal finalScore,
        BigDecimal averageGrade,
        int includedCourseCount
    ) {}
}
