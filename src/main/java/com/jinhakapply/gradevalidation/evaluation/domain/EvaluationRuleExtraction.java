package com.jinhakapply.gradevalidation.evaluation.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import com.jinhakapply.gradevalidation.university.domain.University;
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
@Table(name = "evaluation_rule_extraction")
@NoArgsConstructor(access = PROTECTED)
public class EvaluationRuleExtraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @Column(name = "admission_year", nullable = false)
    private int admissionYear;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "text_page_count", nullable = false)
    private int textPageCount;

    @Enumerated(STRING)
    @Column(nullable = false, length = 30)
    private RuleExtractionStatus status;

    @Enumerated(STRING)
    @Column(name = "selection_strategy", length = 40)
    private SelectionStrategy selectionStrategy;

    @Column(name = "selection_count")
    private Integer selectionCount;

    @Column(name = "grade_weights_csv", length = 100)
    private String gradeWeightsCsv;

    @Column(name = "apply_grade_weights")
    private Boolean applyGradeWeights;

    @Column(name = "grade_scores_csv", length = 255)
    private String gradeScoresCsv;

    @Column(name = "achievement_scores_csv", length = 100)
    private String achievementScoresCsv;

    @Column(name = "subject_categories_csv", length = 100)
    private String subjectCategoriesCsv;

    @Column(name = "include_third_year_second_semester")
    private Boolean includeThirdYearSecondSemester;

    @Enumerated(STRING)
    @Column(name = "rounding_mode", length = 20)
    private RoundingMode roundingMode;

    @Column(name = "source_pages", length = 255)
    private String sourcePages;

    @Column(name = "overall_confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal overallConfidence;

    @Column(name = "missing_fields", length = 500)
    private String missingFields;

    @Column(length = 2000)
    private String warnings;

    @Column(name = "draft_rule_id")
    private Long draftRuleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static EvaluationRuleExtraction create(
        University university,
        int admissionYear,
        String originalFileName,
        String fileSha256,
        int pageCount,
        int textPageCount,
        SelectionStrategy selectionStrategy,
        Integer selectionCount,
        List<BigDecimal> gradeWeights,
        Boolean applyGradeWeights,
        List<BigDecimal> gradeScores,
        List<BigDecimal> achievementScores,
        List<SubjectCategory> subjectCategories,
        Boolean includeThirdYearSecondSemester,
        RoundingMode roundingMode,
        String sourcePages,
        BigDecimal overallConfidence,
        List<String> missingFields,
        List<String> warnings
    ) {
        EvaluationRuleExtraction extraction = new EvaluationRuleExtraction();
        extraction.university = university;
        extraction.admissionYear = admissionYear;
        extraction.originalFileName = originalFileName;
        extraction.fileSha256 = fileSha256;
        extraction.pageCount = pageCount;
        extraction.textPageCount = textPageCount;
        extraction.status = RuleExtractionStatus.EXTRACTED;
        extraction.selectionStrategy = selectionStrategy;
        extraction.selectionCount = selectionCount;
        extraction.gradeWeightsCsv = csv(gradeWeights);
        extraction.applyGradeWeights = applyGradeWeights;
        extraction.gradeScoresCsv = csv(gradeScores);
        extraction.achievementScoresCsv = csv(achievementScores);
        extraction.subjectCategoriesCsv = subjectCategories == null ? null
            : subjectCategories.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(","));
        extraction.includeThirdYearSecondSemester = includeThirdYearSecondSemester;
        extraction.roundingMode = roundingMode;
        extraction.sourcePages = sourcePages;
        extraction.overallConfidence = overallConfidence;
        extraction.missingFields = join(missingFields, 500);
        extraction.warnings = join(warnings, 2000);
        extraction.createdAt = Instant.now();
        extraction.updatedAt = extraction.createdAt;
        return extraction;
    }

    public void attachDraftRule(Long draftRuleId) {
        this.draftRuleId = draftRuleId;
        this.status = RuleExtractionStatus.DRAFT_CREATED;
        this.updatedAt = Instant.now();
    }

    public List<BigDecimal> gradeWeights() {
        return decimals(gradeWeightsCsv);
    }

    public List<BigDecimal> gradeScores() {
        return decimals(gradeScoresCsv);
    }

    public List<BigDecimal> achievementScores() {
        return decimals(achievementScoresCsv);
    }

    public List<SubjectCategory> subjectCategories() {
        if (subjectCategoriesCsv == null || subjectCategoriesCsv.isBlank()) return List.of();
        return java.util.Arrays.stream(subjectCategoriesCsv.split(","))
            .map(SubjectCategory::valueOf)
            .toList();
    }

    public List<String> missingFieldList() {
        return split(missingFields);
    }

    public List<String> warningList() {
        return split(warnings);
    }

    private static String csv(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return null;
        return values.stream().map(BigDecimal::toPlainString).collect(java.util.stream.Collectors.joining(","));
    }

    private static List<BigDecimal> decimals(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(",")).map(BigDecimal::new).toList();
    }

    private static String join(List<String> values, int maxLength) {
        if (values == null || values.isEmpty()) return null;
        String joined = String.join("||", values);
        return joined.length() > maxLength ? joined.substring(0, maxLength) : joined;
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split("\\|\\|"));
    }
}
