package com.jinhakapply.gradevalidation.transcript.dto;

import java.math.BigDecimal;

import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;

public record StudentSummaryResponse(
    Long studentId,
    int admissionYear,
    String applicantNumber,
    String name,
    String highSchoolCode,
    String highSchoolName,
    Integer graduationYear,
    EducationBackground educationBackground,
    GraduationStatus graduationStatus,
    long courseCount,
    BigDecimal averageGrade
) {
    public static StudentSummaryResponse of(
        Student student,
        long courseCount,
        BigDecimal averageGrade
    ) {
        return new StudentSummaryResponse(
            student.getId(),
            student.getAdmissionYear(),
            student.getApplicantNumber(),
            student.getName(),
            student.getHighSchoolCode(),
            student.getHighSchoolName(),
            student.getGraduationYear(),
            student.getEducationBackground(),
            student.getGraduationStatus(),
            courseCount,
            averageGrade
        );
    }
}
