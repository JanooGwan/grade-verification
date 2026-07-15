package com.jinhakapply.gradevalidation.transcript.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStudentRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 30) String highSchoolCode,
    @Size(max = 150) String highSchoolName,
    @Min(1900) @Max(2100) Integer graduationYear
) {}
