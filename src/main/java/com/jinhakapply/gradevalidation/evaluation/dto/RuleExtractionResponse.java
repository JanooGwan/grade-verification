package com.jinhakapply.gradevalidation.evaluation.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleExtraction;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleExtractionEvidence;
import com.jinhakapply.gradevalidation.evaluation.domain.RuleExtractionStatus;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;

public record RuleExtractionResponse(
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
    Candidate candidate,
    List<String> missingFields,
    List<String> warnings,
    List<Evidence> evidence,
    Instant createdAt
) {
    public static RuleExtractionResponse of(
        EvaluationRuleExtraction extraction,
        List<EvaluationRuleExtractionEvidence> evidence
    ) {
        Candidate candidate = new Candidate(
            extraction.getSelectionStrategy(),
            extraction.getSelectionCount(),
            extraction.gradeWeights(),
            extraction.gradeScores(),
            extraction.achievementScores(),
            extraction.subjectCategories(),
            extraction.getIncludeThirdYearSecondSemester(),
            extraction.getRoundingMode(),
            extraction.getSourcePages()
        );
        return new RuleExtractionResponse(
            extraction.getId(),
            extraction.getUniversity().getId(),
            extraction.getUniversity().getName(),
            extraction.getAdmissionYear(),
            extraction.getOriginalFileName(),
            extraction.getFileSha256(),
            extraction.getPageCount(),
            extraction.getTextPageCount(),
            extraction.getStatus(),
            extraction.getDraftRuleId(),
            extraction.getOverallConfidence(),
            candidate,
            extraction.missingFieldList(),
            extraction.warningList(),
            evidence.stream().map(Evidence::from).toList(),
            extraction.getCreatedAt()
        );
    }

    public record Candidate(
        SelectionStrategy selectionStrategy,
        Integer selectionCount,
        List<BigDecimal> gradeWeights,
        List<BigDecimal> gradeScores,
        List<BigDecimal> achievementScores,
        List<SubjectCategory> subjectCategories,
        Boolean includeThirdYearSecondSemester,
        RoundingMode roundingMode,
        String sourcePages
    ) {
    }

    public record Evidence(
        String fieldKey,
        int pageNumber,
        String excerpt,
        BigDecimal confidence
    ) {
        private static Evidence from(EvaluationRuleExtractionEvidence evidence) {
            return new Evidence(
                evidence.getFieldKey(),
                evidence.getPageNumber(),
                evidence.getExcerpt(),
                evidence.getConfidence()
            );
        }
    }
}
