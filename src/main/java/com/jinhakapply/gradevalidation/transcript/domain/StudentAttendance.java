package com.jinhakapply.gradevalidation.transcript.domain;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

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
@Table(name = "student_attendance", uniqueConstraints = @UniqueConstraint(
    name = "uk_student_attendance_year", columnNames = {"student_id", "school_year"}
))
@NoArgsConstructor(access = PROTECTED)
public class StudentAttendance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    @ManyToOne(fetch = LAZY) @JoinColumn(name = "source_import_id")
    private StudentTranscriptImport sourceImport;
    @Column(name = "school_year", nullable = false) private int schoolYear;
    @Column(name = "unexcused_absence_days", nullable = false) private int unexcusedAbsenceDays;
    @Column(name = "unexcused_tardy_count", nullable = false) private int unexcusedTardyCount;
    @Column(name = "unexcused_early_leave_count", nullable = false) private int unexcusedEarlyLeaveCount;
    @Column(name = "unexcused_class_absence_count", nullable = false) private int unexcusedClassAbsenceCount;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public static StudentAttendance create(Student student, int schoolYear) {
        StudentAttendance attendance = new StudentAttendance();
        attendance.student = student;
        attendance.schoolYear = schoolYear;
        attendance.createdAt = LocalDateTime.now();
        attendance.updatedAt = attendance.createdAt;
        return attendance;
    }

    public void update(int absenceDays, int tardyCount, int earlyLeaveCount, int classAbsenceCount) {
        this.unexcusedAbsenceDays = absenceDays;
        this.unexcusedTardyCount = tardyCount;
        this.unexcusedEarlyLeaveCount = earlyLeaveCount;
        this.unexcusedClassAbsenceCount = classAbsenceCount;
        this.updatedAt = LocalDateTime.now();
    }

    public void attachSourceImport(StudentTranscriptImport transcriptImport) {
        this.sourceImport = transcriptImport;
    }
}
