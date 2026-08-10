package com.jinhakapply.gradevalidation.operation.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.jinhakapply.gradevalidation.operation.service.OperationalMetrics;

public record OperationsDashboardResponse(
    long universities,
    long students,
    long transcriptCourses,
    long transcriptImports,
    long studentApplications,
    long verificationRuns,
    long ruleExtractions,
    RuleCounts rules,
    List<UniversityDataStatus> universityDataStatuses,
    OperationalMetrics.Snapshot http
) {
    public record RuleCounts(long draft, long verified, long published, long retired) {}

    public record UniversityDataStatus(
        Long universityId,
        String universityCode,
        String universityName,
        boolean active,
        Integer admissionYear,
        boolean studentDataPresent,
        long studentCount,
        long transcriptCourseCount,
        long applicationCount,
        String latestImportStatus,
        String latestImportFileName,
        LocalDateTime latestImportAt,
        boolean verificationDataPresent,
        long verificationResultCount,
        LocalDateTime latestVerificationAt
    ) {}
}
