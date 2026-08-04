package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SyuImportScoreExcelWriterTest {

    @Test
    void convertsEquivalentAbsenceDaysAtEveryGuidebookBoundary() {
        assertThat(SyuImportScoreExcelWriter.attendanceScore(3)).isEqualByComparingTo("100");
        assertThat(SyuImportScoreExcelWriter.attendanceScore(4)).isEqualByComparingTo("98");
        assertThat(SyuImportScoreExcelWriter.attendanceScore(7)).isEqualByComparingTo("98");
        assertThat(SyuImportScoreExcelWriter.attendanceScore(8)).isEqualByComparingTo("96");
        assertThat(SyuImportScoreExcelWriter.attendanceScore(12)).isEqualByComparingTo("96");
        assertThat(SyuImportScoreExcelWriter.attendanceScore(13)).isEqualByComparingTo("94");
        assertThat(SyuImportScoreExcelWriter.attendanceScore(20)).isEqualByComparingTo("94");
        assertThat(SyuImportScoreExcelWriter.attendanceScore(21)).isEqualByComparingTo("90");
        assertThat(SyuImportScoreExcelWriter.attendanceScore(40)).isEqualByComparingTo("90");
        assertThat(SyuImportScoreExcelWriter.attendanceScore(41)).isEqualByComparingTo("0");
    }
}
