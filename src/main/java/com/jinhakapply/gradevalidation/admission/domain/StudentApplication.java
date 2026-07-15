package com.jinhakapply.gradevalidation.admission.domain;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.jinhakapply.gradevalidation.transcript.domain.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "student_application", uniqueConstraints = @UniqueConstraint(
    name = "uk_student_application", columnNames = {"student_id", "recruitment_unit_id"}
))
@NoArgsConstructor(access = PROTECTED)
public class StudentApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "recruitment_unit_id", nullable = false)
    private RecruitmentUnit recruitmentUnit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static StudentApplication create(Student student, RecruitmentUnit recruitmentUnit) {
        StudentApplication application = new StudentApplication();
        application.student = student;
        application.recruitmentUnit = recruitmentUnit;
        application.createdAt = LocalDateTime.now();
        application.updatedAt = application.createdAt;
        return application;
    }
}
