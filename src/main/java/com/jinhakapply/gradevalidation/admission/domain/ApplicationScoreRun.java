package com.jinhakapply.gradevalidation.admission.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "application_score_run")
@NoArgsConstructor(access = PROTECTED)
public class ApplicationScoreRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    @ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "application_id", nullable = false)
    private StudentApplication application;
    @ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "rule_id", nullable = false)
    private EvaluationRule rule;
    @Column(name = "rule_version", nullable = false) private int ruleVersion;
    @Enumerated(STRING) @Column(nullable = false, length = 30) private ApplicationScoreStatus status;
    @Enumerated(STRING) @Column(name = "education_background", nullable = false, length = 40)
    private EducationBackground educationBackground;
    @Column(name = "academic_base_score", nullable = false, precision = 14, scale = 6)
    private BigDecimal academicBaseScore;
    @Column(name = "academic_score", nullable = false, precision = 14, scale = 6)
    private BigDecimal academicScore;
    @Column(name = "attendance_score", precision = 14, scale = 6) private BigDecimal attendanceScore;
    @Column(name = "additional_score", precision = 14, scale = 6) private BigDecimal additionalScore;
    @Column(name = "school_violence_deduction", nullable = false, precision = 14, scale = 6)
    private BigDecimal schoolViolenceDeduction;
    @Column(name = "quantitative_subtotal", nullable = false, precision = 14, scale = 6)
    private BigDecimal quantitativeSubtotal;
    @Column(name = "score_after_deduction", nullable = false, precision = 14, scale = 6)
    private BigDecimal scoreAfterDeduction;
    @Column(name = "final_score", precision = 14, scale = 6) private BigDecimal finalScore;
    @Lob @Column(name = "result_json", nullable = false, columnDefinition = "LONGTEXT") private String resultJson;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public static ApplicationScoreRun create(
        StudentApplication application,
        EvaluationRule rule,
        EducationBackground educationBackground,
        ApplicationScoreResult result,
        String resultJson
    ) {
        ApplicationScoreRun run = new ApplicationScoreRun();
        run.student = application.getStudent();
        run.application = application;
        run.rule = rule;
        run.ruleVersion = rule.getVersion();
        run.status = result.status();
        run.educationBackground = educationBackground;
        run.academicBaseScore = result.academicBaseScore();
        run.academicScore = result.academicScore();
        run.attendanceScore = result.attendanceScore();
        run.additionalScore = result.additionalScore();
        run.schoolViolenceDeduction = result.schoolViolenceDeduction();
        run.quantitativeSubtotal = result.quantitativeSubtotal();
        run.scoreAfterDeduction = result.scoreAfterDeduction();
        run.finalScore = result.finalScore();
        run.resultJson = resultJson;
        run.createdAt = LocalDateTime.now();
        return run;
    }
}
