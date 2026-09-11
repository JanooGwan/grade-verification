package com.jinhakapply.gradevalidation.transcript.service;

import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;

record ApplicantSchoolInfoRow(
    int rowNumber,
    Integer admissionYear,
    String applicantNumber,
    Integer graduationYear,
    String highSchoolCode,
    String highSchoolName,
    String departmentCode,
    String sourceHighSchoolType,
    String sourceHighSchoolCategory,
    String applicantHighSchoolCategoryCode,
    EducationBackground educationBackground,
    HighSchoolType highSchoolType,
    com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus graduationStatus,
    java.time.LocalDate graduationDate
) {
    ApplicantSchoolInfoRow(int rowNumber, Integer admissionYear, String applicantNumber,
        Integer graduationYear, String highSchoolCode, String highSchoolName, String departmentCode,
        String sourceHighSchoolType, String sourceHighSchoolCategory, String applicantHighSchoolCategoryCode,
        EducationBackground educationBackground, HighSchoolType highSchoolType) {
        this(rowNumber, admissionYear, applicantNumber, graduationYear, highSchoolCode, highSchoolName,
            departmentCode, sourceHighSchoolType, sourceHighSchoolCategory, applicantHighSchoolCategoryCode,
            educationBackground, highSchoolType, null, null);
    }
}
