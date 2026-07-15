package com.jinhakapply.gradevalidation.evaluation.dto;

import java.math.BigDecimal;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VerifyGradeRequest(
    @NotNull Long ruleId,
    @NotEmpty List<@Valid CourseGrade> courses
) {
    public record CourseGrade(
        @Min(1) @Max(3) int schoolYear,
        @Min(1) @Max(2) int semester,
        @NotNull SubjectCategory subjectCategory,
        @NotBlank @Size(max = 100) String courseName,
        @Min(1) @Max(9) Integer grade,
        AchievementLevel achievement,
        @DecimalMin(value = "0") BigDecimal rawScore,
        @DecimalMin(value = "0") BigDecimal meanScore,
        @DecimalMin(value = "0.0001") BigDecimal standardDeviation,
        @Min(1) Integer studentCount,
        boolean careerSubject,
        boolean professionalCourse,
        @NotNull @DecimalMin(value = "0.01") BigDecimal credits
    ) {}
}
