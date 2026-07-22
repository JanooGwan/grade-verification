package com.jinhakapply.gradevalidation.transcript.dto;

import java.math.BigDecimal;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.transcript.domain.LegacyAchievement;
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
    @NotNull GradeScale gradeScale,
    AchievementLevel achievement,
    @DecimalMin("0") BigDecimal rawScore,
    @DecimalMin("0") BigDecimal meanScore,
    @DecimalMin("0.0001") BigDecimal standardDeviation,
    @Min(1) Integer studentCount,
    @Min(1) Integer rankPosition,
    @Min(1) Integer tiedRankCount,
    LegacyAchievement legacyAchievement,
    @NotNull @DecimalMin("0.01") BigDecimal credits,
    boolean careerSubject,
    boolean professionalCourse
) {
    public UpsertTranscriptCourseRequest(
        int schoolYear, int semester, SubjectCategory subjectCategory, String courseName, Integer grade,
        AchievementLevel achievement, BigDecimal rawScore, BigDecimal meanScore, BigDecimal standardDeviation,
        Integer studentCount, BigDecimal credits, boolean careerSubject, boolean professionalCourse
    ) {
        this(schoolYear, semester, subjectCategory, courseName, grade, GradeScale.NINE_LEVEL, achievement,
            rawScore, meanScore, standardDeviation, studentCount, null, null, null, credits,
            careerSubject, professionalCourse);
    }
}
