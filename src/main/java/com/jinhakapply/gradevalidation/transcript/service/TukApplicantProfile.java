package com.jinhakapply.gradevalidation.transcript.service;

import java.time.LocalDate;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.transcript.domain.Student;

record TukApplicantProfile(EducationBackground educationBackground, HighSchoolType highSchoolType,
    GraduationStatus graduationStatus, LocalDate graduationDate) {
    void applyTo(Student student) {
        student.updateGraduationDate(graduationDate);
        student.updateCommonEvaluationProfile(educationBackground, highSchoolType, graduationStatus, null);
    }
}
