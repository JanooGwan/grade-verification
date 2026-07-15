package com.jinhakapply.gradevalidation.evaluation.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementConversion;
import com.jinhakapply.gradevalidation.evaluation.domain.ScoreAggregation;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateEvaluationRuleRequest(
    @NotNull @Positive Long universityId,
    @NotBlank @Size(max = 120) String name,
    @Min(2000) @Max(2100) int admissionYear,
    @NotBlank @Size(max = 100) String admissionType,
    @NotBlank @Size(max = 120) String recruitmentUnit,
    @Min(1) int version,
    @NotNull @Size(min = 3, max = 3) List<@NotNull @Min(0) BigDecimal> gradeWeights,
    @NotNull @Size(min = 6, max = 6) List<@NotNull @Min(0) BigDecimal> subjectWeights,
    @NotNull @Size(min = 9, max = 9) List<@NotNull @Min(0) BigDecimal> gradeScores,
    @NotNull SelectionStrategy selectionStrategy,
    @Min(0) @Max(100) int selectionCount,
    @Min(0) @Max(100) int achievementSelectionCount,
    @NotNull ScoreAggregation scoreAggregation,
    @NotNull AchievementConversion achievementConversion,
    boolean includeThirdYearSecondSemester,
    boolean includeProfessionalCourses,
    boolean normalizeGradeWeights,
    @Min(0) @Max(8) int intermediateScale,
    @NotNull RoundingMode intermediateRounding,
    @Min(0) @Max(8) int finalScale,
    @NotNull RoundingMode finalRounding,
    @NotNull @DecimalMin(value = "0.0001") BigDecimal scoreMultiplier,
    @NotNull @Size(min = 3, max = 3) List<@NotNull @DecimalMin(value = "1") BigDecimal> achievementGrades,
    @NotNull @Size(min = 3, max = 3) List<@NotNull @Min(0) BigDecimal> achievementScores,
    @NotNull @Size(min = 6, max = 6) List<@NotNull @Min(1) Integer> subjectPriorities,
    @Size(max = 255) String sourceDocument,
    @Size(max = 50) String sourcePages,
    @Size(max = 1000) String interpretationNote,
    @Size(max = 1000) String changeSummary
) {}
