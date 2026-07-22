package com.jinhakapply.gradevalidation.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.jinhakapply.gradevalidation.global.exception.CustomException;
import org.junit.jupiter.api.Test;

class LegacyGradeConversionPolicyTest {

    @Test
    void reproducesGuidebookRankPercentileExamplesAndBoundaries() {
        assertThat(LegacyGradeConversionPolicy.rankPercentile(30, null, 126, 5))
            .isEqualByComparingTo("23.80952");
        assertThat(LegacyGradeConversionPolicy.rankPercentile(21, null, 385, 5))
            .isEqualByComparingTo("5.45455");
        assertThat(LegacyGradeConversionPolicy.rankPercentile(30, 4, 126, 2))
            .isEqualByComparingTo("25.00");
        assertThat(LegacyGradeConversionPolicy.gradeForPercentile(new BigDecimal("4.00000"))).isEqualTo(1);
        assertThat(LegacyGradeConversionPolicy.gradeForPercentile(new BigDecimal("4.00001"))).isEqualTo(2);
        assertThat(LegacyGradeConversionPolicy.gradeForPercentile(new BigDecimal("23.80952"))).isEqualTo(4);
    }

    @Test
    void rejectsImpossibleRankAndTieRanges() {
        assertThatThrownBy(() -> LegacyGradeConversionPolicy.rankPercentile(101, 1, 100, 5))
            .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> LegacyGradeConversionPolicy.rankPercentile(99, 3, 100, 5))
            .isInstanceOf(CustomException.class);
    }
}
