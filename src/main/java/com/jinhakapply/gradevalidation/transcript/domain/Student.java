package com.jinhakapply.gradevalidation.transcript.domain;

import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;
import java.math.BigDecimal;

import com.jinhakapply.gradevalidation.university.domain.University;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "student",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_student_university_admission_applicant",
        columnNames = {"university_id", "admission_year", "applicant_number"}
    )
)
@NoArgsConstructor(access = PROTECTED)
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "education_background", nullable = false, length = 40)
    private EducationBackground educationBackground;

    @Enumerated(EnumType.STRING)
    @Column(name = "high_school_type", nullable = false, length = 40)
    private HighSchoolType highSchoolType;

    @Enumerated(EnumType.STRING)
    @Column(name = "graduation_status", nullable = false, length = 30)
    private GraduationStatus graduationStatus;

    @Column(name = "ged_average_score", precision = 5, scale = 2)
    private BigDecimal gedAverageScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Student(
        University university,
        int admissionYear,
        String applicantNumber,
        String name,
        String highSchoolCode,
        String highSchoolName,
        Integer graduationYear
    ) {
        this.university = university;
        this.admissionYear = admissionYear;
        this.applicantNumber = applicantNumber;
        updateProfile(name, highSchoolCode, highSchoolName, graduationYear);
        this.educationBackground = EducationBackground.DOMESTIC_HIGH_SCHOOL;
        this.highSchoolType = HighSchoolType.GENERAL;
        this.graduationStatus = inferGraduationStatus(graduationYear);
        this.createdAt = this.updatedAt;
    }

    public static Student create(
        University university,
        int admissionYear,
        String applicantNumber,
        String name,
        String highSchoolCode,
        String highSchoolName,
        Integer graduationYear
    ) {
        return new Student(
            university,
            admissionYear,
            applicantNumber,
            name,
            highSchoolCode,
            highSchoolName,
            graduationYear
        );
    }

    /**
     * Kept only for isolated domain tests. Persisted students must be created
     * with their owning university.
     */
    public static Student create(
        int admissionYear,
        String applicantNumber,
        String name,
        String highSchoolCode,
        String highSchoolName,
        Integer graduationYear
    ) {
        return new Student(
            null, admissionYear, applicantNumber, name, highSchoolCode, highSchoolName, graduationYear
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
        this.graduationStatus = inferGraduationStatus(graduationYear);
        this.updatedAt = LocalDateTime.now();
    }

    private GraduationStatus inferGraduationStatus(Integer graduationYear) {
        return graduationYear != null && graduationYear < admissionYear
            ? GraduationStatus.GRADUATE : GraduationStatus.EXPECTED_GRADUATE;
    }

    public void updateCommonEvaluationProfile(
        EducationBackground educationBackground,
        HighSchoolType highSchoolType,
        GraduationStatus graduationStatus,
        BigDecimal gedAverageScore
    ) {
        this.educationBackground = educationBackground;
        this.highSchoolType = highSchoolType;
        this.graduationStatus = graduationStatus;
        this.gedAverageScore = gedAverageScore;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateCommonEvaluationProfile(
        EducationBackground educationBackground,
        GraduationStatus graduationStatus,
        BigDecimal gedAverageScore
    ) {
        updateCommonEvaluationProfile(educationBackground, HighSchoolType.GENERAL, graduationStatus, gedAverageScore);
    }
}
