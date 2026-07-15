package com.jinhakapply.gradevalidation.transcript.service;

import java.math.BigDecimal;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;

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
    AchievementLevel achievement,
    BigDecimal rawScore,
    BigDecimal meanScore,
    BigDecimal standardDeviation,
    Integer studentCount,
    BigDecimal credits,
    boolean careerSubject,
    boolean professionalCourse
) {
}
