package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SyuSourceImportProcessorTest {

    @Test
    void acceptsOneSourceYearWithoutRequiringItToMatchTheRuleYear() {
        assertThat(SyuSourceImportProcessor.requireSingleSourceAdmissionYear(Set.of(2026)))
            .isEqualTo(2026);
    }

    @Test
    void rejectsAWorkbookContainingMultipleSourceYears() {
        assertThatThrownBy(() ->
            SyuSourceImportProcessor.requireSingleSourceAdmissionYear(Set.of(2026, 2027)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("하나의 입학연도")
            .hasMessageContaining("2026")
            .hasMessageContaining("2027");
    }
}
