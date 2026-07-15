package com.jinhakapply.gradevalidation.admission.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.jinhakapply.gradevalidation.admission.domain.VerificationRun;

public record VerificationHistoryResponse(
    Long verificationRunId,
    Long applicationId,
    Long ruleId,
    String ruleName,
    int ruleVersion,
    String universityName,
    String admissionType,
    String recruitmentUnit,
    BigDecimal finalScore,
    BigDecimal averageGrade,
    int includedCourseCount,
    int excludedCourseCount,
    LocalDateTime createdAt
) {
    public static VerificationHistoryResponse from(VerificationRun run) {
        return new VerificationHistoryResponse(
            run.getId(), run.getApplication() == null ? null : run.getApplication().getId(),
            run.getRule().getId(), run.getRule().getName(), run.getRuleVersion(),
            run.getRule().getUniversity().getName(), run.getRule().getAdmissionType(),
            run.getRule().getRecruitmentUnit(), run.getFinalScore(), run.getAverageGrade(),
            run.getIncludedCourseCount(), run.getExcludedCourseCount(), run.getCreatedAt()
        );
    }
}
