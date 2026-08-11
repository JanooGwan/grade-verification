package com.jinhakapply.gradevalidation.transcript.service;

import java.math.BigDecimal;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;

record TranscriptBatchVerificationResult(
    List<Success> successes,
    List<Failure> failures
) {
    record Success(
        TransferApplicationRow application,
        String studentName,
        GradeVerificationResponse verification,
        List<SelectedCourse> selectedCourses,
        List<IntermediateCalculation> intermediateCalculations,
        ApplicantSchoolInfoRow schoolInfo
    ) {}

    record SelectedCourse(
        TranscriptExcelRow source,
        GradeVerificationResponse.CourseCalculation calculation
    ) {}

    record IntermediateCalculation(
        String groupType,
        String groupName,
        boolean selected,
        Integer selectionOrder,
        int courseCount,
        BigDecimal totalCredits,
        BigDecimal gradeTimesCreditsSum,
        BigDecimal averageGrade,
        BigDecimal convertedScoreTimesCreditsSum,
        BigDecimal averageConvertedScore
    ) {}

    record Failure(
        TransferApplicationRow application,
        String studentName,
        int availableCourseCount,
        String code,
        String reason
    ) {}
}
