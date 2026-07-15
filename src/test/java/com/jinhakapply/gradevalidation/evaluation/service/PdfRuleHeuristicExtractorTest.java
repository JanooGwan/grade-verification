package com.jinhakapply.gradevalidation.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import org.junit.jupiter.api.Test;

class PdfRuleHeuristicExtractorTest {
    private final PdfRuleHeuristicExtractor extractor = new PdfRuleHeuristicExtractor();

    @Test
    void extractsOnlyCandidatesSupportedByPageEvidence() {
        RuleExtractionAnalysis result = extractor.extractFromPages(List.of(
            "학생부 반영 방법 교과성적 반영 교과영역 국어 영어 수학 사회 과학 영역에서 "
                + "이수한 모든 과목을 반영한다. 학년별 차등 없이 반영하며 졸업예정자는 3학년 1학기까지 반영한다.",
            "석차등급 1 2 3 4 5 6 7 8 9 환산점수 100 99 98 97 96 95 90 80 50 "
                + "진로선택과목 성취도 A B C 환산점수 100 95 90 최종 점수는 소수 둘째 자리에서 반올림한다."
        ));

        assertThat(result.selectionStrategy()).isEqualTo(SelectionStrategy.ALL_COURSES);
        assertThat(result.gradeWeights()).containsExactly(
            new BigDecimal("33.3333"), new BigDecimal("33.3333"), new BigDecimal("33.3334"));
        assertThat(result.gradeScores()).hasSize(9);
        assertThat(result.achievementScores()).containsExactly(
            new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("90"));
        assertThat(result.subjectCategories()).containsExactly(
            SubjectCategory.KOREAN, SubjectCategory.MATH, SubjectCategory.ENGLISH,
            SubjectCategory.SOCIAL, SubjectCategory.SCIENCE);
        assertThat(result.includeThirdYearSecondSemester()).isFalse();
        assertThat(result.roundingMode()).isEqualTo(RoundingMode.HALF_UP);
        assertThat(result.evidence()).isNotEmpty();
        assertThat(result.sourcePages()).isEqualTo("1, 2");
    }

    @Test
    void leavesGradeScoresEmptyWhenDifferentTablesAreFound() {
        RuleExtractionAnalysis result = extractor.extractFromPages(List.of(
            "학생부 반영 교과성적 석차등급 1 2 3 4 5 6 7 8 9 환산점수 100 99 98 97 96 95 94 93 92",
            "모집단위별 학생부 반영 교과성적 석차등급 1 2 3 4 5 6 7 8 9 환산점수 100 98 96 94 92 90 88 86 84"
        ));

        assertThat(result.gradeScores()).isEmpty();
        assertThat(result.warnings()).anyMatch(message -> message.contains("다른 석차등급표"));
    }
}
