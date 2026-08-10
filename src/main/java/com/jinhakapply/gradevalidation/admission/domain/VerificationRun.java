package com.jinhakapply.gradevalidation.admission.domain;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "verification_run")
@NoArgsConstructor(access = PROTECTED)
public class VerificationRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = LAZY) @JoinColumn(name = "source_import_id")
    private StudentTranscriptImport sourceImport;
    @ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    @ManyToOne(fetch = LAZY) @JoinColumn(name = "application_id")
    private StudentApplication application;
    @ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "rule_id", nullable = false)
    private EvaluationRule rule;
    @Column(name = "rule_version", nullable = false) private int ruleVersion;
    @Column(name = "final_score", nullable = false, precision = 14, scale = 6) private BigDecimal finalScore;
    @Column(name = "average_grade", precision = 10, scale = 6) private BigDecimal averageGrade;
    @Column(name = "included_course_count", nullable = false) private int includedCourseCount;
    @Column(name = "excluded_course_count", nullable = false) private int excludedCourseCount;
    @Lob @Column(name = "result_json", nullable = false, columnDefinition = "LONGTEXT") private String resultJson;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public static VerificationRun create(
        StudentApplication application,
        EvaluationRule rule,
        GradeVerificationResponse result,
        String resultJson
    ) {
        VerificationRun run = new VerificationRun();
        run.student = application.getStudent();
        run.application = application;
        run.rule = rule;
        run.ruleVersion = result.ruleVersion();
        run.finalScore = result.finalScore();
        run.averageGrade = result.averageGrade();
        run.includedCourseCount = result.includedCourseCount();
        run.excludedCourseCount = result.excludedCourseCount();
        run.resultJson = resultJson;
        run.createdAt = LocalDateTime.now();
        return run;
    }
}
