package com.jinhakapply.gradevalidation.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void normalizesPolicyTextWithoutLosingKoreanLettersOrDigits() {
        assertThat(TextNormalizer.normalizePolicyText(" 학생부교과(지역균형) 2027 "))
            .isEqualTo("학생부교과지역균형2027");
        assertThat(TextNormalizer.normalizePolicyText(null)).isEmpty();
    }
}
