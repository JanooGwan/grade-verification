package com.jinhakapply.gradevalidation.evaluation.dto;

import java.math.BigDecimal;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.ScoreAggregation;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;

public record GradeVerificationResponse(
    Long ruleId, String ruleName, int ruleVersion, String universityName,
    String admissionType, String recruitmentUnit, BigDecimal finalScore,
    BigDecimal baseScore, BigDecimal averageGrade, SelectionStrategy selectionStrategy, ScoreAggregation scoreAggregation,
    String sourceDocument, String sourcePages,
    int includedCourseCount, int excludedCourseCount, List<CourseCalculation> calculations,
    List<String> warnings
) {
    public record CourseCalculation(
        String courseName, int schoolYear, int semester, SubjectCategory subjectCategory,
        SubjectCategory appliedSubjectCategory,
        Integer grade, AchievementLevel achievement, BigDecimal effectiveGrade,
        BigDecimal convertedScore, BigDecimal gradeWeight,
        BigDecimal subjectWeight, BigDecimal credits, BigDecimal appliedWeight,
        BigDecimal weightedScore, boolean included, String exclusionReason
    ) {}
}
