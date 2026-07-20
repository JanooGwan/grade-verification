package com.jinhakapply.gradevalidation.transcript.dto;

import java.math.BigDecimal;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertTranscriptCourseRequest(
    @Min(1) @Max(3) int schoolYear,
    @Min(1) @Max(2) int semester,
    @NotNull SubjectCategory subjectCategory,
    @NotBlank @Size(max = 100) String courseName,
    @Min(1) @Max(9) Integer grade,
    AchievementLevel achievement,
    @DecimalMin("0") BigDecimal rawScore,
    @DecimalMin("0") BigDecimal meanScore,
    @DecimalMin("0.0001") BigDecimal standardDeviation,
    @Min(1) Integer studentCount,
    @NotNull @DecimalMin("0.01") BigDecimal credits,
    boolean careerSubject,
    boolean professionalCourse
) {}
