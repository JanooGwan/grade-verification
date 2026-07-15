package com.jinhakapply.gradevalidation.transcript.domain;

import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "student",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_student_admission_applicant",
        columnNames = {"admission_year", "applicant_number"}
    )
)
@NoArgsConstructor(access = PROTECTED)
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admission_year", nullable = false)
    private int admissionYear;

    @Column(name = "applicant_number", nullable = false, length = 50)
    private String applicantNumber;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "high_school_code", length = 30)
    private String highSchoolCode;

    @Column(name = "high_school_name", length = 150)
    private String highSchoolName;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Student(
        int admissionYear,
        String applicantNumber,
        String name,
        String highSchoolCode,
        String highSchoolName,
        Integer graduationYear
    ) {
        this.admissionYear = admissionYear;
        this.applicantNumber = applicantNumber;
        updateProfile(name, highSchoolCode, highSchoolName, graduationYear);
        this.createdAt = this.updatedAt;
    }

    public static Student create(
        int admissionYear,
        String applicantNumber,
        String name,
        String highSchoolCode,
        String highSchoolName,
        Integer graduationYear
    ) {
        return new Student(
            admissionYear,
            applicantNumber,
            name,
            highSchoolCode,
            highSchoolName,
            graduationYear
        );
    }

    public void updateProfile(
        String name,
        String highSchoolCode,
        String highSchoolName,
        Integer graduationYear
    ) {
        this.name = name;
        this.highSchoolCode = highSchoolCode;
        this.highSchoolName = highSchoolName;
        this.graduationYear = graduationYear;
        this.updatedAt = LocalDateTime.now();
    }
}
