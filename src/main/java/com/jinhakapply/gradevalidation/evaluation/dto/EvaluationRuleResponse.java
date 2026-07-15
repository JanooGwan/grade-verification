package com.jinhakapply.gradevalidation.evaluation.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.domain.AchievementConversion;
import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.ScoreAggregation;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;

public record EvaluationRuleResponse(
    Long id, Long universityId, String universityName, String name, int admissionYear,
    String admissionType, String recruitmentUnit, int version,
    List<BigDecimal> gradeWeights, List<BigDecimal> subjectWeights,
    List<BigDecimal> gradeScores, SelectionStrategy selectionStrategy, int selectionCount,
    int achievementSelectionCount, ScoreAggregation scoreAggregation,
    AchievementConversion achievementConversion, boolean includeThirdYearSecondSemester,
    boolean includeProfessionalCourses, int intermediateScale, RoundingMode intermediateRounding,
    boolean normalizeGradeWeights,
    int finalScale, RoundingMode finalRounding, BigDecimal scoreMultiplier,
    List<BigDecimal> achievementGrades, List<BigDecimal> achievementScores, List<Integer> subjectPriorities,
    String sourceDocument, String sourcePages, String interpretationNote, String changeSummary, Long extractionId,
    boolean active, EvaluationRuleStatus status, String reviewer, String reviewNote, Instant reviewedAt,
    String publishedBy, String publicationNote, Instant publishedAt,
    String retiredBy, String retireNote, Instant retiredAt
) {
    public static EvaluationRuleResponse from(EvaluationRule rule) {
        return new EvaluationRuleResponse(rule.getId(), rule.getUniversity().getId(), rule.getUniversity().getName(),
            rule.getName(), rule.getAdmissionYear(), rule.getAdmissionType(), rule.getRecruitmentUnit(), rule.getVersion(),
            List.of(rule.getGrade1Weight(), rule.getGrade2Weight(), rule.getGrade3Weight()),
            List.of(rule.getKoreanWeight(), rule.getMathWeight(), rule.getEnglishWeight(), rule.getSocialWeight(), rule.getScienceWeight(), rule.getOtherWeight()),
            IntStream.rangeClosed(1, 9).mapToObj(rule.getGradeScores()::get).toList(),
            rule.getSelectionStrategy(), rule.getSelectionCount(), rule.getAchievementSelectionCount(),
            rule.getScoreAggregation(), rule.getAchievementConversion(), rule.isIncludeThirdYearSecondSemester(),
            rule.isIncludeProfessionalCourses(), rule.getIntermediateScale(), rule.getIntermediateRounding(), rule.isNormalizeGradeWeights(),
            rule.getFinalScale(), rule.getFinalRounding(), rule.getScoreMultiplier(),
            List.of(rule.getAchievementGrades().get(AchievementLevel.A), rule.getAchievementGrades().get(AchievementLevel.B), rule.getAchievementGrades().get(AchievementLevel.C)),
            List.of(rule.getAchievementScores().get(AchievementLevel.A), rule.getAchievementScores().get(AchievementLevel.B), rule.getAchievementScores().get(AchievementLevel.C)),
            java.util.Arrays.stream(SubjectCategory.values()).map(rule.getSubjectPriorities()::get).toList(),
            rule.getSourceDocument(), rule.getSourcePages(), rule.getInterpretationNote(), rule.getChangeSummary(), rule.getExtractionId(),
            rule.isActive(), rule.getStatus(), rule.getReviewer(), rule.getReviewNote(), rule.getReviewedAt(),
            rule.getPublishedBy(), rule.getPublicationNote(), rule.getPublishedAt(),
            rule.getRetiredBy(), rule.getRetireNote(), rule.getRetiredAt());
    }
}
