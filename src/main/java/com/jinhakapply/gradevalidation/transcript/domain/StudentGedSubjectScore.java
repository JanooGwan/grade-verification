package com.jinhakapply.gradevalidation.transcript.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "student_ged_subject_score")
@NoArgsConstructor(access = PROTECTED)
public class StudentGedSubjectScore {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    @Enumerated(STRING) @Column(name = "subject_type", nullable = false, length = 30)
    private GedSubjectType subjectType;
    @Column(name = "subject_name", nullable = false, length = 100)
    private String subjectName;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static StudentGedSubjectScore create(Student student, GedSubjectType type, String name, BigDecimal score) {
        StudentGedSubjectScore value = new StudentGedSubjectScore();
        value.student = student;
        value.subjectType = type;
        value.subjectName = name.trim();
        value.score = score;
        value.createdAt = LocalDateTime.now();
        value.updatedAt = value.createdAt;
        return value;
    }
}

