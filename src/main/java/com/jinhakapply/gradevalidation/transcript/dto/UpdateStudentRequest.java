package com.jinhakapply.gradevalidation.transcript.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;

public record UpdateStudentRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 30) String highSchoolCode,
    @Size(max = 150) String highSchoolName,
    @Min(1900) @Max(2100) Integer graduationYear,
    LocalDate graduationDate
) {
    public UpdateStudentRequest(String name, String highSchoolCode, String highSchoolName, Integer graduationYear) {
        this(name, highSchoolCode, highSchoolName, graduationYear, null);
    }

    @AssertTrue(message = "졸업일은 1900~2100년 범위이며 졸업연도와 일치해야 합니다.")
    public boolean isGraduationDateConsistent() {
        return graduationDate == null || (graduationDate.getYear() >= 1900 && graduationDate.getYear() <= 2100
            && (graduationYear == null || graduationYear == graduationDate.getYear()));
    }
}
