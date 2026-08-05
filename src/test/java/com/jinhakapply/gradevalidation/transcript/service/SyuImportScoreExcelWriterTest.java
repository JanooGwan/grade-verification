package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.Test;

class SyuImportScoreExcelWriterTest {

    @Test
    void exportsOnlyApplicantSummaryColumnsWithoutAdmissionOrRecruitmentScenarios() {
        assertThat(SyuImportScoreExcelWriter.resultSheetName()).isEqualTo("지원자별 환산 결과");
        assertThat(SyuImportScoreExcelWriter.resultHeaders()).containsExactly(
            "수험번호", "전체 과목수", "환산 가능 과목수", "반영 과목수",
            "환산점수×이수단위 합", "반영 이수단위 합",
            "1-1 학기", "1-2 학기", "2-1 학기", "2-2 학기", "3-1 학기", "3-2 학기",
            "최종 환산값(1,000점 기준)"
        );
        assertThat(SyuImportScoreExcelWriter.resultHeaders())
            .noneMatch(header -> header.contains("전형") || header.contains("모집단위")
                || header.contains("중간값") || header.contains("검증 상태"));
    }

    @Test
    void calculatesSemesterIntermediateAsWeightedAverage() {
        assertThat(SyuImportScoreExcelWriter.semesterIntermediate(
            new BigDecimal("585.5"), new BigDecimal("6"), 10, RoundingMode.DOWN
        )).isEqualByComparingTo("97.5833333333");
    }

    @Test
    void leavesSemesterIntermediateBlankWhenThereAreNoIncludedCourses() {
        assertThat(SyuImportScoreExcelWriter.semesterIntermediate(
            BigDecimal.ZERO, BigDecimal.ZERO, 10, RoundingMode.DOWN
        )).isNull();
    }
}
