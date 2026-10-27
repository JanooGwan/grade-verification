package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.ScoreAggregation;
import com.jinhakapply.gradevalidation.university.domain.University;
import org.junit.jupiter.api.Test;

class MjcArchiveScoresTest {
    @Test
    void computesWeightedGedScoreAndKeepsRawTieValuesWithoutInventingSchoolCourses() {
        var result = MjcArchiveScores.ged(rule(), "특별[특성화고]", source());
        assertThat(result.averageGrade()).isEqualByComparingTo("6.82353");
        assertThat(result.baseScore()).isEqualByComparingTo("41.76470");
        assertThat(result.finalScore()).isEqualByComparingTo("417.64700");
        assertThat(result.calculationSummary().totalIncludedCredits()).isEqualByComparingTo("34");
        assertThat(result.calculations()).isEmpty();
        assertThat(result.sourceValues()).containsEntry("동점자 국어·영어·수학 합", "255")
            .containsEntry("동점자 사회·과학·한국사 합", "225").containsEntry("동점자 합격일", "2026-08-01");
    }

    @Test
    void refusesMissingRequiredGedSubjectAndInapplicableTrack() {
        var complete = source();
        var missing = new MjcArchiveApplicant("A", "SYNTHETIC", 1, "GED", null, "G", null, null,
            complete.gedSubjects().subList(1, complete.gedSubjects().size()));
        assertThatThrownBy(() -> MjcArchiveScores.ged(rule(), "특별[특성화고]", missing))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MjcArchiveScores.ged(rule(), "농어촌", complete))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesRealZeroAndBoundaryGrades() {
        assertThat(MjcArchiveScores.grade(BigDecimal.ZERO)).isEqualTo(9);
        assertThat(MjcArchiveScores.grade(new BigDecimal("97.999"))).isEqualTo(3);
        assertThat(MjcArchiveScores.grade(new BigDecimal("98"))).isEqualTo(2);
        assertThat(MjcArchiveScores.grade(new BigDecimal("100"))).isEqualTo(1);
    }

    private static MjcArchiveApplicant source() {
        String[] names = {"국어", "영어", "수학", "한국사", "사회", "과학", "도덕", "기술"};
        int[] scores = {85, 85, 85, 85, 75, 65, 96, 98};
        var subjects = new java.util.ArrayList<MjcArchiveApplicant.GedSubject>();
        for (int i = 0; i < names.length; i++) subjects.add(new MjcArchiveApplicant.GedSubject(
            "S" + i, names[i], BigDecimal.valueOf(scores[i])));
        return new MjcArchiveApplicant("A", "SYNTHETIC", 1, "GED", null, "G", null, "2026-08-01", subjects);
    }

    private static EvaluationRule rule() {
        EvaluationRule rule = mock(EvaluationRule.class);
        when(rule.getUniversity()).thenReturn(University.create("MJC", "명지전문대학"));
        Map<Integer, BigDecimal> grades = new LinkedHashMap<>();
        for (int i = 1; i <= 9; i++) grades.put(i, BigDecimal.valueOf(110 - i * 10L));
        when(rule.getGradeScores()).thenReturn(grades);
        when(rule.getScoreMultiplier()).thenReturn(BigDecimal.TEN);
        when(rule.getFinalScale()).thenReturn(5);
        when(rule.getFinalRounding()).thenReturn(RoundingMode.HALF_UP);
        when(rule.getScoreAggregation()).thenReturn(ScoreAggregation.AVERAGE_GRADE_THEN_SCORE);
        return rule;
    }
}
