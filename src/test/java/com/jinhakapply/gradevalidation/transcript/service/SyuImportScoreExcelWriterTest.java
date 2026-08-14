package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.Test;

class SyuImportScoreExcelWriterTest {

    @Test
    void exportsAdmissionScenarioAndAvailableApplicationScoreComponents() {
        assertThat(SyuImportScoreExcelWriter.resultSheetName()).isEqualTo("전형별 환산 결과");
        assertThat(SyuImportScoreExcelWriter.resultHeaders()).containsExactly(
            "수험번호", "전형명", "모집단위", "산출 상태",
            "전체 과목수", "환산 가능 과목수", "반영 과목수",
            "환산점수×이수단위 합", "반영 이수단위 합",
            "1-1 학기", "1-2 학기", "2-1 학기", "2-2 학기", "3-1 학기", "3-2 학기",
            "교과 기준점수(100점)", "교과 반영점수", "환산 결석일수", "출결 반영점수",
            "학교폭력 감점", "현재 산출점수", "전형 최종점수", "전형 총점 만점",
            "추가입력·미산출 요소", "경고·오류", "규칙 버전"
        );
        assertThat(SyuImportScoreExcelWriter.resultHeaders())
            .contains("전형명", "모집단위", "출결 반영점수", "추가입력·미산출 요소");
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
