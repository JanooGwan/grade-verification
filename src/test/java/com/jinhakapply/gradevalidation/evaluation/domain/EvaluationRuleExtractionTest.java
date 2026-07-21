package com.jinhakapply.gradevalidation.evaluation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.jinhakapply.gradevalidation.university.domain.University;
import org.junit.jupiter.api.Test;

class EvaluationRuleExtractionTest {

    @Test
    void preservesExtractedCandidateCollections() {
        EvaluationRuleExtraction extraction = extraction();

        assertThat(extraction.getStatus()).isEqualTo(RuleExtractionStatus.EXTRACTED);
        assertThat(extraction.gradeWeights()).containsExactly(
            new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("50")
        );
        assertThat(extraction.gradeScores()).containsExactly(
            new BigDecimal("100"), new BigDecimal("95")
        );
        assertThat(extraction.subjectCategories()).containsExactly(SubjectCategory.KOREAN, SubjectCategory.MATH);
        assertThat(extraction.missingFieldList()).containsExactly("achievementScores");
        assertThat(extraction.warningList()).containsExactly("표 근거 재검수 필요");
    }

    @Test
    void attachingDraftChangesLifecycleAndStoresRuleId() {
        EvaluationRuleExtraction extraction = extraction();

        extraction.attachDraftRule(77L);

        assertThat(extraction.getStatus()).isEqualTo(RuleExtractionStatus.DRAFT_CREATED);
        assertThat(extraction.getDraftRuleId()).isEqualTo(77L);
        assertThat(extraction.getUpdatedAt()).isAfterOrEqualTo(extraction.getCreatedAt());
    }

    private EvaluationRuleExtraction extraction() {
        return EvaluationRuleExtraction.create(
            University.create("TUK", "한국공학대학교"),
            2027,
            "guideline.pdf",
            "a".repeat(64),
            30,
            28,
            SelectionStrategy.ALL_COURSES,
            null,
            List.of(new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("50")),
            true,
            List.of(new BigDecimal("100"), new BigDecimal("95")),
            List.of(),
            List.of(SubjectCategory.KOREAN, SubjectCategory.MATH),
            false,
            RoundingMode.HALF_UP,
            "10-12",
            new BigDecimal("0.9000"),
            List.of("achievementScores"),
            List.of("표 근거 재검수 필요")
        );
    }
}
