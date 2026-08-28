package com.jinhakapply.gradevalidation.transcript.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.global.util.TextNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "student_transcript_course",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_transcript_course_normalized",
        columnNames = {"student_id", "school_year", "semester", "course_name_normalized"}
    )
)
@NoArgsConstructor(access = PROTECTED)
public class StudentTranscriptCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "source_import_id")
    private StudentTranscriptImport sourceImport;

    @Column(name = "school_year", nullable = false)
    private int schoolYear;

    @Column(nullable = false)
    private int semester;

    @Enumerated(STRING)
    @Column(name = "subject_category", nullable = false, length = 20)
    private SubjectCategory subjectCategory;

    @Column(name = "course_name", nullable = false, length = 100)
    private String courseName;

    @Column(name = "course_name_normalized", nullable = false, length = 100)
    private String courseNameNormalized;

    @Column(name = "grade_value")
    private Integer grade;

    @Enumerated(STRING)
    @Column(name = "grade_scale", nullable = false, length = 20)
    private GradeScale gradeScale;

    @Enumerated(STRING)
    @Column(length = 10)
    private AchievementLevel achievement;

    @Column(name = "raw_score", precision = 8, scale = 4)
    private BigDecimal rawScore;

    @Column(name = "mean_score", precision = 8, scale = 4)
    private BigDecimal meanScore;

    @Column(name = "standard_deviation", precision = 8, scale = 4)
    private BigDecimal standardDeviation;

    @Column(name = "student_count")
    private Integer studentCount;

    @Column(name = "rank_position")
    private Integer rankPosition;

    @Column(name = "tied_rank_count")
    private Integer tiedRankCount;

    @Enumerated(STRING)
    @Column(name = "legacy_achievement", length = 10)
    private LegacyAchievement legacyAchievement;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal credits;

    @Column(name = "career_subject", nullable = false)
    private boolean careerSubject;

    @Column(name = "professional_course", nullable = false)
    private boolean professionalCourse;

    @Column(name = "vocational_training_semester", nullable = false)
    private boolean vocationalTrainingSemester;

    @Column(name = "source_file_name", nullable = false, length = 255)
    private String sourceFileName;

    @Column(name = "source_row_number", nullable = false)
    private int sourceRowNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private StudentTranscriptCourse(
        Student student,
        int schoolYear,
        int semester,
        SubjectCategory subjectCategory,
        String courseName
    ) {
        this.student = student;
        this.schoolYear = schoolYear;
        this.semester = semester;
        this.subjectCategory = subjectCategory;
        this.courseName = courseName.trim();
        this.courseNameNormalized = TextNormalizer.normalizeCourseName(courseName);
        this.gradeScale = GradeScale.NINE_LEVEL;
        this.createdAt = LocalDateTime.now();
    }

    public static StudentTranscriptCourse create(
        Student student,
        int schoolYear,
        int semester,
        SubjectCategory subjectCategory,
        String courseName
    ) {
        return new StudentTranscriptCourse(student, schoolYear, semester, subjectCategory, courseName);
    }

    public void attachSourceImport(StudentTranscriptImport sourceImport) {
        this.sourceImport = sourceImport;
    }

    public void updateScore(
        Integer grade,
        AchievementLevel achievement,
        BigDecimal rawScore,
        BigDecimal meanScore,
        BigDecimal standardDeviation,
        Integer studentCount,
        BigDecimal credits,
        boolean careerSubject,
        boolean professionalCourse,
        String sourceFileName,
        int sourceRowNumber
    ) {
        updateScore(grade, GradeScale.NINE_LEVEL, achievement, rawScore, meanScore, standardDeviation,
            studentCount, null, null, null, credits, careerSubject, professionalCourse, sourceFileName,
            sourceRowNumber);
    }

    public void updateScore(
        Integer grade,
        GradeScale gradeScale,
        AchievementLevel achievement,
        BigDecimal rawScore,
        BigDecimal meanScore,
        BigDecimal standardDeviation,
        Integer studentCount,
        Integer rankPosition,
        Integer tiedRankCount,
        LegacyAchievement legacyAchievement,
        BigDecimal credits,
        boolean careerSubject,
        boolean professionalCourse,
        String sourceFileName,
        int sourceRowNumber
    ) {
        updateScore(
            grade, gradeScale, achievement, rawScore, meanScore, standardDeviation, studentCount,
            rankPosition, tiedRankCount, legacyAchievement, credits, careerSubject, professionalCourse,
            vocationalTrainingSemester, sourceFileName, sourceRowNumber
        );
    }

    public void updateScore(
        Integer grade,
        GradeScale gradeScale,
        AchievementLevel achievement,
        BigDecimal rawScore,
        BigDecimal meanScore,
        BigDecimal standardDeviation,
        Integer studentCount,
        Integer rankPosition,
        Integer tiedRankCount,
        LegacyAchievement legacyAchievement,
        BigDecimal credits,
        boolean careerSubject,
        boolean professionalCourse,
        boolean vocationalTrainingSemester,
        String sourceFileName,
        int sourceRowNumber
    ) {
        this.grade = grade;
        this.gradeScale = gradeScale == null ? GradeScale.NINE_LEVEL : gradeScale;
        this.achievement = achievement;
        this.rawScore = rawScore;
        this.meanScore = meanScore;
        this.standardDeviation = standardDeviation;
        this.studentCount = studentCount;
        this.rankPosition = rankPosition;
        this.tiedRankCount = tiedRankCount;
        this.legacyAchievement = legacyAchievement;
        this.credits = credits;
        this.careerSubject = careerSubject;
        this.professionalCourse = professionalCourse;
        this.vocationalTrainingSemester = vocationalTrainingSemester;
        this.sourceFileName = sourceFileName;
        this.sourceRowNumber = sourceRowNumber;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateCourse(
        int schoolYear,
        int semester,
        SubjectCategory subjectCategory,
        String courseName,
        Integer grade,
        AchievementLevel achievement,
        BigDecimal rawScore,
        BigDecimal meanScore,
        BigDecimal standardDeviation,
        Integer studentCount,
        BigDecimal credits,
        boolean careerSubject,
        boolean professionalCourse,
        String sourceFileName,
        int sourceRowNumber
    ) {
        updateCourse(schoolYear, semester, subjectCategory, courseName, grade, GradeScale.NINE_LEVEL,
            achievement, rawScore, meanScore, standardDeviation, studentCount, null, null, null,
            credits, careerSubject, professionalCourse, sourceFileName, sourceRowNumber);
    }

    public void updateCourse(
        int schoolYear,
        int semester,
        SubjectCategory subjectCategory,
        String courseName,
        Integer grade,
        GradeScale gradeScale,
        AchievementLevel achievement,
        BigDecimal rawScore,
        BigDecimal meanScore,
        BigDecimal standardDeviation,
        Integer studentCount,
        Integer rankPosition,
        Integer tiedRankCount,
        LegacyAchievement legacyAchievement,
        BigDecimal credits,
        boolean careerSubject,
        boolean professionalCourse,
        String sourceFileName,
        int sourceRowNumber
    ) {
        updateCourse(
            schoolYear, semester, subjectCategory, courseName, grade, gradeScale, achievement,
            rawScore, meanScore, standardDeviation, studentCount, rankPosition, tiedRankCount,
            legacyAchievement, credits, careerSubject, professionalCourse, vocationalTrainingSemester,
            sourceFileName, sourceRowNumber
        );
    }

    public void updateCourse(
        int schoolYear,
        int semester,
        SubjectCategory subjectCategory,
        String courseName,
        Integer grade,
        GradeScale gradeScale,
        AchievementLevel achievement,
        BigDecimal rawScore,
        BigDecimal meanScore,
        BigDecimal standardDeviation,
        Integer studentCount,
        Integer rankPosition,
        Integer tiedRankCount,
        LegacyAchievement legacyAchievement,
        BigDecimal credits,
        boolean careerSubject,
        boolean professionalCourse,
        boolean vocationalTrainingSemester,
        String sourceFileName,
        int sourceRowNumber
    ) {
        this.schoolYear = schoolYear;
        this.semester = semester;
        this.subjectCategory = subjectCategory;
        this.courseName = courseName.trim();
        this.courseNameNormalized = TextNormalizer.normalizeCourseName(courseName);
        updateScore(grade, gradeScale, achievement, rawScore, meanScore, standardDeviation, studentCount,
            rankPosition, tiedRankCount, legacyAchievement, credits, careerSubject, professionalCourse,
            vocationalTrainingSemester, sourceFileName, sourceRowNumber);
    }
}
