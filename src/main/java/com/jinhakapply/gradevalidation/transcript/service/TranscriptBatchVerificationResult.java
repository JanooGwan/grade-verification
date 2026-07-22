package com.jinhakapply.gradevalidation.transcript.service;

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
        List<SelectedCourse> selectedCourses
    ) {}

    record SelectedCourse(
        TranscriptExcelRow source,
        GradeVerificationResponse.CourseCalculation calculation
    ) {}

    record Failure(
        TransferApplicationRow application,
        String studentName,
        int availableCourseCount,
        String code,
        String reason
    ) {}
}
