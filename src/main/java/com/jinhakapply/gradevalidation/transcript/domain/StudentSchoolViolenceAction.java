package com.jinhakapply.gradevalidation.transcript.domain;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "student_school_violence_action")
@NoArgsConstructor(access = PROTECTED)
public class StudentSchoolViolenceAction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    @Column(name = "school_year") private Integer schoolYear;
    @Column(name = "action_number", nullable = false) private int actionNumber;
    @Column(name = "action_date") private LocalDate actionDate;
    @Column(nullable = false) private boolean active;
    @Column(length = 500) private String note;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public static StudentSchoolViolenceAction create(
        Student student, Integer schoolYear, int actionNumber, LocalDate actionDate, boolean active, String note
    ) {
        StudentSchoolViolenceAction action = new StudentSchoolViolenceAction();
        action.student = student;
        action.schoolYear = schoolYear;
        action.actionNumber = actionNumber;
        action.actionDate = actionDate;
        action.active = active;
        action.note = note;
        action.createdAt = LocalDateTime.now();
        action.updatedAt = action.createdAt;
        return action;
    }
}
