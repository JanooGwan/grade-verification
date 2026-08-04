package com.jinhakapply.gradevalidation.evaluation.domain;

import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy;
import com.jinhakapply.gradevalidation.evaluation.policy.SelectionPolicyConverter;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.transcript.domain.LegacyAchievement;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "evaluation_rule")
@NoArgsConstructor(access = PROTECTED)
public class EvaluationRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "admission_year", nullable = false)
    private int admissionYear;

    @Column(name = "admission_type", nullable = false, length = 100)
    private String admissionType;

    @Column(name = "recruitment_unit", nullable = false, length = 120)
    private String recruitmentUnit;

    @Column(nullable = false)
    private int version;

    @Column(name = "grade1_weight", nullable = false, precision = 7, scale = 4)
    private BigDecimal grade1Weight;

    @Column(name = "grade2_weight", nullable = false, precision = 7, scale = 4)
    private BigDecimal grade2Weight;

    @Column(name = "grade3_weight", nullable = false, precision = 7, scale = 4)
    private BigDecimal grade3Weight;

    @Column(name = "korean_weight", nullable = false, precision = 7, scale = 4)
    private BigDecimal koreanWeight;

    @Column(name = "math_weight", nullable = false, precision = 7, scale = 4)
    private BigDecimal mathWeight;

    @Column(name = "english_weight", nullable = false, precision = 7, scale = 4)
    private BigDecimal englishWeight;

    @Column(name = "social_weight", nullable = false, precision = 7, scale = 4)
    private BigDecimal socialWeight;

    @Column(name = "science_weight", nullable = false, precision = 7, scale = 4)
    private BigDecimal scienceWeight;

    @Column(name = "other_weight", nullable = false, precision = 7, scale = 4)
    private BigDecimal otherWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_strategy", nullable = false, length = 40)
    private SelectionStrategy selectionStrategy;

    @Column(name = "selection_count", nullable = false)
    private int selectionCount;

    @Column(name = "achievement_selection_count", nullable = false)
    private int achievementSelectionCount;

    @Column(name = "minimum_course_count", nullable = false)
    private int minimumCourseCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_aggregation", nullable = false, length = 40)
    private ScoreAggregation scoreAggregation;

    @Enumerated(EnumType.STRING)
    @Column(name = "achievement_conversion", nullable = false, length = 30)
    private AchievementConversion achievementConversion;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_grade_scale", nullable = false, length = 20)
    private GradeScale inputGradeScale;

    @Column(name = "include_third_year_second_semester", nullable = false)
    private boolean includeThirdYearSecondSemester;

    @Column(name = "include_third_year_second_semester_for_graduates", nullable = false)
    private boolean includeThirdYearSecondSemesterForGraduates;

    @Column(name = "include_professional_courses", nullable = false)
    private boolean includeProfessionalCourses;

    @Column(name = "apply_grade_weights", nullable = false)
    private boolean applyGradeWeights;

    @Column(name = "normalize_grade_weights", nullable = false)
    private boolean normalizeGradeWeights;

    @Column(name = "intermediate_scale", nullable = false)
    private int intermediateScale;

    @Enumerated(EnumType.STRING)
    @Column(name = "intermediate_rounding", nullable = false, length = 20)
    private RoundingMode intermediateRounding;

    @Column(name = "final_scale", nullable = false)
    private int finalScale;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_rounding", nullable = false, length = 20)
    private RoundingMode finalRounding;

    @Column(name = "score_multiplier", nullable = false, precision = 9, scale = 4)
    private BigDecimal scoreMultiplier;

    @Convert(converter = SelectionPolicyConverter.class)
    @Column(name = "selection_policy", columnDefinition = "LONGTEXT")
    private CourseSelectionPolicy selectionPolicy;

    @Column(name = "source_document", length = 255)
    private String sourceDocument;

    @Column(name = "source_pages", length = 50)
    private String sourcePages;

    @Column(name = "interpretation_note", length = 1000)
    private String interpretationNote;

    @Column(name = "change_summary", length = 1000)
    private String changeSummary;

    @Column(name = "extraction_id")
    private Long extractionId;

    @Column(nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvaluationRuleStatus status;

    @Column(length = 100)
    private String reviewer;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "published_by", length = 100)
    private String publishedBy;
    @Column(name = "publication_note", length = 1000)
    private String publicationNote;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "retired_by", length = 100)
    private String retiredBy;
    @Column(name = "retire_note", length = 1000)
    private String retireNote;
    @Column(name = "retired_at")
    private Instant retiredAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "evaluation_rule_grade_score", joinColumns = @JoinColumn(name = "rule_id"))
    @MapKeyColumn(name = "grade_value")
    @Column(name = "converted_score", nullable = false, precision = 7, scale = 4)
    private java.util.Map<Integer, BigDecimal> gradeScores = new java.util.LinkedHashMap<>();

    @ElementCollection
    @CollectionTable(name = "evaluation_rule_achievement_grade", joinColumns = @JoinColumn(name = "rule_id"))
    @MapKeyColumn(name = "achievement_level")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "converted_grade", nullable = false, precision = 5, scale = 2)
    private java.util.Map<AchievementLevel, BigDecimal> achievementGrades = new java.util.LinkedHashMap<>();

    @ElementCollection
    @CollectionTable(name = "evaluation_rule_achievement_score", joinColumns = @JoinColumn(name = "rule_id"))
    @MapKeyColumn(name = "achievement_level")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "converted_score", nullable = false, precision = 7, scale = 4)
    private java.util.Map<AchievementLevel, BigDecimal> achievementScores = new java.util.LinkedHashMap<>();

    @ElementCollection
    @CollectionTable(name = "evaluation_rule_legacy_achievement_grade", joinColumns = @JoinColumn(name = "rule_id"))
    @MapKeyColumn(name = "legacy_achievement")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "converted_grade", nullable = false, precision = 5, scale = 2)
    private java.util.Map<LegacyAchievement, BigDecimal> legacyAchievementGrades = new java.util.LinkedHashMap<>();

    @ElementCollection
    @CollectionTable(name = "evaluation_rule_subject_priority", joinColumns = @JoinColumn(name = "rule_id"))
    @MapKeyColumn(name = "subject_category")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "priority_value", nullable = false)
    private java.util.Map<SubjectCategory, Integer> subjectPriorities = new java.util.LinkedHashMap<>();

    public static EvaluationRule create(University university, String name, int admissionYear,
        String admissionType, String recruitmentUnit, int version, List<BigDecimal> gradeWeights,
        List<BigDecimal> subjectWeights, List<BigDecimal> gradeScores, SelectionStrategy selectionStrategy,
        int selectionCount, int achievementSelectionCount, int minimumCourseCount, ScoreAggregation scoreAggregation,
        AchievementConversion achievementConversion, boolean includeThirdYearSecondSemester,
        boolean includeThirdYearSecondSemesterForGraduates,
        boolean includeProfessionalCourses, boolean applyGradeWeights, boolean normalizeGradeWeights,
        int intermediateScale,
        RoundingMode intermediateRounding,
        int finalScale, RoundingMode finalRounding, BigDecimal scoreMultiplier,
        List<BigDecimal> achievementGrades, List<BigDecimal> achievementScores, List<Integer> subjectPriorities,
        String sourceDocument, String sourcePages, String interpretationNote, String changeSummary) {
        EvaluationRule rule = new EvaluationRule();
        rule.university = university;
        rule.name = name.trim();
        rule.admissionYear = admissionYear;
        rule.admissionType = admissionType.trim();
        rule.recruitmentUnit = recruitmentUnit.trim();
        rule.version = version;
        rule.grade1Weight = gradeWeights.get(0);
        rule.grade2Weight = gradeWeights.get(1);
        rule.grade3Weight = gradeWeights.get(2);
        rule.koreanWeight = subjectWeights.get(0);
        rule.mathWeight = subjectWeights.get(1);
        rule.englishWeight = subjectWeights.get(2);
        rule.socialWeight = subjectWeights.get(3);
        rule.scienceWeight = subjectWeights.get(4);
        rule.otherWeight = subjectWeights.get(5);
        for (int i = 0; i < gradeScores.size(); i++)
            rule.gradeScores.put(i + 1, gradeScores.get(i));
        rule.selectionStrategy = selectionStrategy;
        rule.selectionCount = selectionCount;
        rule.achievementSelectionCount = achievementSelectionCount;
        rule.minimumCourseCount = minimumCourseCount;
        rule.scoreAggregation = scoreAggregation;
        rule.achievementConversion = achievementConversion;
        rule.inputGradeScale = GradeScale.NINE_LEVEL;
        rule.includeThirdYearSecondSemester = includeThirdYearSecondSemester;
        rule.includeThirdYearSecondSemesterForGraduates = includeThirdYearSecondSemesterForGraduates;
        rule.includeProfessionalCourses = includeProfessionalCourses;
        rule.applyGradeWeights = applyGradeWeights;
        rule.normalizeGradeWeights = normalizeGradeWeights;
        rule.intermediateScale = intermediateScale;
        rule.intermediateRounding = intermediateRounding;
        rule.finalScale = finalScale;
        rule.finalRounding = finalRounding;
        rule.scoreMultiplier = scoreMultiplier;
        AchievementLevel[] levels = {AchievementLevel.A, AchievementLevel.B, AchievementLevel.C};
        for (int i = 0; i < levels.length; i++) {
            rule.achievementGrades.put(levels[i], achievementGrades.get(i));
            rule.achievementScores.put(levels[i], achievementScores.get(i));
        }
        rule.legacyAchievementGrades.put(LegacyAchievement.SU, BigDecimal.ONE);
        rule.legacyAchievementGrades.put(LegacyAchievement.WOO, new BigDecimal("3"));
        rule.legacyAchievementGrades.put(LegacyAchievement.MI, new BigDecimal("5"));
        rule.legacyAchievementGrades.put(LegacyAchievement.YANG, new BigDecimal("7"));
        rule.legacyAchievementGrades.put(LegacyAchievement.GA, new BigDecimal("9"));
        SubjectCategory[] categories = SubjectCategory.values();
        for (int i = 0; i < categories.length; i++)
            rule.subjectPriorities.put(categories[i], subjectPriorities.get(i));
        rule.sourceDocument = sourceDocument == null ? null : sourceDocument.trim();
        rule.sourcePages = sourcePages == null ? null : sourcePages.trim();
        rule.interpretationNote = clean(interpretationNote);
        rule.changeSummary = clean(changeSummary);
        rule.active = false;
        rule.status = EvaluationRuleStatus.DRAFT;
        rule.createdAt = Instant.now();
        rule.updatedAt = rule.createdAt;
        return rule;
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public void markVerified(String reviewer, String reviewNote) {
        this.status = EvaluationRuleStatus.VERIFIED;
        this.reviewer = reviewer.trim();
        this.reviewNote = clean(reviewNote);
        this.reviewedAt = Instant.now();
        this.updatedAt = this.reviewedAt;
    }

    public void attachExtraction(Long extractionId) {
        this.extractionId = extractionId;
        this.updatedAt = Instant.now();
    }

    public void configureSelectionPolicy(CourseSelectionPolicy selectionPolicy) {
        this.selectionPolicy = selectionPolicy;
        this.updatedAt = Instant.now();
    }

    public void configureInputGradeScale(GradeScale gradeScale, List<BigDecimal> legacyGrades) {
        this.inputGradeScale = gradeScale == null ? GradeScale.NINE_LEVEL : gradeScale;
        LegacyAchievement[] values = LegacyAchievement.values();
        if (legacyGrades == null || legacyGrades.size() != values.length) {
            throw new IllegalArgumentException("수·우·미·양·가 환산등급은 5개여야 합니다.");
        }
        this.legacyAchievementGrades.clear();
        for (int index = 0; index < values.length; index++) {
            this.legacyAchievementGrades.put(values[index], legacyGrades.get(index));
        }
        this.updatedAt = Instant.now();
    }

    public void publish(String publishedBy, String publicationNote) {
        this.status = EvaluationRuleStatus.PUBLISHED;
        this.active = true;
        this.publishedBy = publishedBy.trim();
        this.publicationNote = clean(publicationNote);
        this.publishedAt = Instant.now();
        this.updatedAt = this.publishedAt;
    }

    public void retire(String retiredBy, String retireNote) {
        this.status = EvaluationRuleStatus.RETIRED;
        this.active = false;
        this.retiredBy = retiredBy.trim();
        this.retireNote = clean(retireNote);
        this.retiredAt = Instant.now();
        this.updatedAt = this.retiredAt;
    }

    public boolean isPublished() {
        return active && status == EvaluationRuleStatus.PUBLISHED;
    }

    public BigDecimal gradeWeight(int grade) {
        return switch (grade) {
            case 1 -> grade1Weight;
            case 2 -> grade2Weight;
            case 3 -> grade3Weight;
            default -> BigDecimal.ZERO;
        };
    }

    public BigDecimal subjectWeight(SubjectCategory category) {
        return switch (category) {
            case KOREAN -> koreanWeight;
            case MATH -> mathWeight;
            case ENGLISH -> englishWeight;
            case SOCIAL -> socialWeight;
            case SCIENCE -> scienceWeight;
            case OTHER -> otherWeight;
        };
    }
}
