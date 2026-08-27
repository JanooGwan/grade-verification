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
@Table(name = "student_legacy_grade_summary")
@NoArgsConstructor(access = PROTECTED)
public class StudentLegacyGradeSummary {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    @Enumerated(STRING) @Column(name = "summary_type", nullable = false, length = 20)
    private LegacySummaryType summaryType;
    @Column(name = "school_year", nullable = false)
    private int schoolYear;
    private Integer semester;
    @Column(name = "rank_position", nullable = false)
    private int rankPosition;
    @Column(name = "tied_rank_count")
    private Integer tiedRankCount;
    @Column(name = "cohort_size", nullable = false)
    private int cohortSize;
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal credits;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static StudentLegacyGradeSummary create(Student student, LegacySummaryType type, int schoolYear,
        Integer semester, int rankPosition, Integer tiedRankCount, int cohortSize, BigDecimal credits) {
        StudentLegacyGradeSummary value = new StudentLegacyGradeSummary();
        value.student = student;
        value.summaryType = type;
        value.schoolYear = schoolYear;
        value.semester = semester;
        value.rankPosition = rankPosition;
        value.tiedRankCount = tiedRankCount;
        value.cohortSize = cohortSize;
        value.credits = credits;
        value.createdAt = LocalDateTime.now();
        value.updatedAt = value.createdAt;
        return value;
    }
}

