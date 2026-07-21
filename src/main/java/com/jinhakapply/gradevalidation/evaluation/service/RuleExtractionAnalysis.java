package com.jinhakapply.gradevalidation.evaluation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;

record RuleExtractionAnalysis(
    int pageCount,
    int textPageCount,
    SelectionStrategy selectionStrategy,
    Integer selectionCount,
    List<BigDecimal> gradeWeights,
    Boolean applyGradeWeights,
    List<BigDecimal> gradeScores,
    List<BigDecimal> achievementScores,
    List<SubjectCategory> subjectCategories,
    Boolean includeThirdYearSecondSemester,
    RoundingMode roundingMode,
    String sourcePages,
    BigDecimal overallConfidence,
    List<String> missingFields,
    List<String> warnings,
    List<Evidence> evidence
) {
    record Evidence(String fieldKey, int pageNumber, String excerpt, BigDecimal confidence) {
    }
}
