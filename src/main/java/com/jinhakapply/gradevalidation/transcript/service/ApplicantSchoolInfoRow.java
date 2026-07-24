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
    HighSchoolType highSchoolType
) {}
