package com.jinhakapply.gradevalidation.transcript.service;

import java.math.BigDecimal;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.transcript.domain.LegacyAchievement;

record TranscriptExcelRow(
    int rowNumber,
    String applicantNumber,
    String studentName,
    String highSchoolCode,
    String highSchoolName,
    Integer graduationYear,
    int schoolYear,
    int semester,
    SubjectCategory subjectCategory,
    String courseName,
    Integer grade,
    GradeScale gradeScale,
    AchievementLevel achievement,
    BigDecimal rawScore,
    BigDecimal meanScore,
    BigDecimal standardDeviation,
    Integer studentCount,
    Integer rankPosition,
    Integer tiedRankCount,
    LegacyAchievement legacyAchievement,
    BigDecimal credits,
    boolean careerSubject,
    boolean professionalCourse,
    boolean vocationalTrainingSemester
) {
    TranscriptExcelRow(
        int rowNumber,
        String applicantNumber,
        String studentName,
        String highSchoolCode,
        String highSchoolName,
        Integer graduationYear,
        int schoolYear,
        int semester,
        SubjectCategory subjectCategory,
        String courseName,
        Integer grade,
        GradeScale gradeScale,
        AchievementLevel achievement,
        BigDecimal rawScore,
        BigDecimal meanScore,
        BigDecimal standardDeviation,
        Integer studentCount,
        Integer rankPosition,
        Integer tiedRankCount,
        LegacyAchievement legacyAchievement,
        BigDecimal credits,
        boolean careerSubject,
        boolean professionalCourse
    ) {
        this(
            rowNumber, applicantNumber, studentName, highSchoolCode, highSchoolName, graduationYear,
            schoolYear, semester, subjectCategory, courseName, grade, gradeScale, achievement, rawScore,
            meanScore, standardDeviation, studentCount, rankPosition, tiedRankCount, legacyAchievement,
            credits, careerSubject, professionalCourse, false
        );
    }

    TranscriptExcelRow(
        int rowNumber,
        String applicantNumber,
        String studentName,
        String highSchoolCode,
        String highSchoolName,
        Integer graduationYear,
        int schoolYear,
        int semester,
        SubjectCategory subjectCategory,
        String courseName,
        Integer grade,
        AchievementLevel achievement,
        BigDecimal rawScore,
        BigDecimal meanScore,
        BigDecimal standardDeviation,
        Integer studentCount,
        BigDecimal credits,
        boolean careerSubject,
        boolean professionalCourse
    ) {
        this(
            rowNumber,
            applicantNumber,
            studentName,
            highSchoolCode,
            highSchoolName,
            graduationYear,
            schoolYear,
            semester,
            subjectCategory,
            courseName,
            grade,
            GradeScale.NINE_LEVEL,
            achievement,
            rawScore,
            meanScore,
            standardDeviation,
            studentCount,
            null,
            null,
            null,
            credits,
            careerSubject,
            professionalCourse,
            false
        );
    }

    TranscriptExcelRow withVocationalTrainingSemester(boolean value) {
        return new TranscriptExcelRow(
            rowNumber, applicantNumber, studentName, highSchoolCode, highSchoolName, graduationYear,
            schoolYear, semester, subjectCategory, courseName, grade, gradeScale, achievement, rawScore,
            meanScore, standardDeviation, studentCount, rankPosition, tiedRankCount, legacyAchievement,
            credits, careerSubject, professionalCourse, value
        );
    }
}
