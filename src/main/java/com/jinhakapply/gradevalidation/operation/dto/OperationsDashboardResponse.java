package com.jinhakapply.gradevalidation.operation.dto;

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
    OperationalMetrics.Snapshot http
) {
    public record RuleCounts(long draft, long verified, long published, long retired) {}
}
