package com.jinhakapply.gradevalidation.evaluation.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleExtraction;
import com.jinhakapply.gradevalidation.evaluation.domain.RuleExtractionStatus;

public record RuleExtractionSummaryResponse(
    Long extractionId,
    Long universityId,
    String universityName,
    int admissionYear,
    String originalFileName,
    String fileSha256,
    int pageCount,
    int textPageCount,
    RuleExtractionStatus status,
    Long draftRuleId,
    BigDecimal overallConfidence,
    int missingFieldCount,
    int warningCount,
    Instant createdAt
) {
    public static RuleExtractionSummaryResponse from(EvaluationRuleExtraction extraction) {
        return new RuleExtractionSummaryResponse(
            extraction.getId(), extraction.getUniversity().getId(), extraction.getUniversity().getName(),
            extraction.getAdmissionYear(), extraction.getOriginalFileName(), extraction.getFileSha256(),
            extraction.getPageCount(), extraction.getTextPageCount(), extraction.getStatus(), extraction.getDraftRuleId(),
            extraction.getOverallConfidence(), extraction.missingFieldList().size(), extraction.warningList().size(),
            extraction.getCreatedAt()
        );
    }
}
