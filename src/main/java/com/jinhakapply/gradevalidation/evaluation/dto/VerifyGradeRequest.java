package com.jinhakapply.gradevalidation.evaluation.dto;

import java.math.BigDecimal;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.transcript.domain.LegacyAchievement;
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
    boolean graduated,
    HighSchoolType highSchoolType,
    Integer graduationYear,
    @NotEmpty List<@Valid CourseGrade> courses
) {
    public VerifyGradeRequest(Long ruleId, List<CourseGrade> courses) {
        this(ruleId, false, HighSchoolType.GENERAL, null, courses);
    }

    public VerifyGradeRequest(Long ruleId, boolean graduated, List<CourseGrade> courses) {
        this(ruleId, graduated, HighSchoolType.GENERAL, null, courses);
    }

    public VerifyGradeRequest(Long ruleId, boolean graduated, HighSchoolType highSchoolType,
        List<CourseGrade> courses) {
        this(ruleId, graduated, highSchoolType, null, courses);
    }

    public record CourseGrade(
        @Min(1) @Max(3) int schoolYear,
        @Min(1) @Max(2) int semester,
        @NotNull SubjectCategory subjectCategory,
        @NotBlank @Size(max = 100) String courseName,
        @Min(1) @Max(9) Integer grade,
        GradeScale gradeScale,
        AchievementLevel achievement,
        @DecimalMin(value = "0") BigDecimal rawScore,
        @DecimalMin(value = "0") BigDecimal meanScore,
        @DecimalMin(value = "0.0001") BigDecimal standardDeviation,
        @Min(1) Integer studentCount,
        @Min(1) Integer rankPosition,
        @Min(1) Integer tiedRankCount,
        LegacyAchievement legacyAchievement,
        boolean careerSubject,
        boolean professionalCourse,
        boolean vocationalTrainingSemester,
        @NotNull @DecimalMin(value = "0.01") BigDecimal credits
    ) {
        public CourseGrade(
            int schoolYear, int semester, SubjectCategory subjectCategory, String courseName, Integer grade,
            GradeScale gradeScale, AchievementLevel achievement, BigDecimal rawScore, BigDecimal meanScore,
            BigDecimal standardDeviation, Integer studentCount, Integer rankPosition, Integer tiedRankCount,
            LegacyAchievement legacyAchievement, boolean careerSubject, boolean professionalCourse,
            BigDecimal credits
        ) {
            this(
                schoolYear, semester, subjectCategory, courseName, grade, gradeScale, achievement, rawScore,
                meanScore, standardDeviation, studentCount, rankPosition, tiedRankCount, legacyAchievement,
                careerSubject, professionalCourse, false, credits
            );
        }

        public CourseGrade(
            int schoolYear, int semester, SubjectCategory subjectCategory, String courseName, Integer grade,
            AchievementLevel achievement, BigDecimal rawScore, BigDecimal meanScore,
            BigDecimal standardDeviation, Integer studentCount, boolean careerSubject,
            boolean professionalCourse, BigDecimal credits
        ) {
            this(
                schoolYear, semester, subjectCategory, courseName, grade, achievement, rawScore, meanScore,
                standardDeviation, studentCount, careerSubject, professionalCourse, false, credits
            );
        }

        public CourseGrade(
            int schoolYear, int semester, SubjectCategory subjectCategory, String courseName, Integer grade,
            AchievementLevel achievement, BigDecimal rawScore, BigDecimal meanScore,
            BigDecimal standardDeviation, Integer studentCount, boolean careerSubject,
            boolean professionalCourse, boolean vocationalTrainingSemester, BigDecimal credits
        ) {
            this(schoolYear, semester, subjectCategory, courseName, grade, GradeScale.NINE_LEVEL,
                achievement, rawScore, meanScore, standardDeviation, studentCount, null, null, null,
                careerSubject, professionalCourse, vocationalTrainingSemester, credits);
        }
    }
}
