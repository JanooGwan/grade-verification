package com.jinhakapply.gradevalidation.admission.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.ScoreAggregation;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class VerificationResultExcelWriterTest {
    private final VerificationResultExcelWriter writer = new VerificationResultExcelWriter();

    @Test
    void writesSummaryAndCourseCalculationSheets() throws Exception {
        GradeVerificationResponse response = response();

        byte[] file = writer.write(
            "A-001", "테스트 학생", LocalDateTime.of(2026, 7, 22, 14, 30), response
        );

        assertThat(file).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetName(0)).isEqualTo("검증 요약");
            assertThat(workbook.getSheetName(1)).isEqualTo("과목별 결과");

            var summary = workbook.getSheet("검증 요약");
            assertThat(summary.getRow(0).getCell(0).getStringCellValue()).isEqualTo("성적 검증 결과");
            assertThat(summary.getRow(3).getCell(1).getStringCellValue()).isEqualTo("A-001");
            assertThat(summary.getRow(3).getCell(3).getStringCellValue()).isEqualTo("테스트 학생");

            var courses = workbook.getSheet("과목별 결과");
            assertThat(courses.getRow(2).getCell(0).getStringCellValue()).isEqualTo("반영 여부");
            assertThat(courses.getRow(3).getCell(0).getStringCellValue()).isEqualTo("반영");
            assertThat(courses.getRow(3).getCell(6).getStringCellValue()).isEqualTo("국어");
            assertThat(courses.getRow(3).getCell(22).getNumericCellValue()).isEqualTo(95.5);
            assertThat(courses.getPaneInformation()).isNotNull();
        }
    }

    private GradeVerificationResponse response() {
        var summary = new GradeVerificationResponse.CalculationSummary(
            "환산점수 합계 / 반영 과목 수",
            new BigDecimal("3.00"), new BigDecimal("95.50"),
            new BigDecimal("3.00"), new BigDecimal("95.50"),
            BigDecimal.ONE, new BigDecimal("3.00"), new BigDecimal("2.00"),
            BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("95.50"),
            6, RoundingMode.HALF_UP, 2, RoundingMode.HALF_UP,
            Map.of(1, BigDecimal.ONE)
        );
        var course = new GradeVerificationResponse.CourseCalculation(
            "국어", 1, 1, SubjectCategory.KOREAN, SubjectCategory.KOREAN,
            2, GradeScale.NINE_LEVEL, AchievementLevel.A,
            2, 1, 100, new BigDecimal("2.00"), null,
            new BigDecimal("2.00"), new BigDecimal("95.50"), BigDecimal.ONE,
            BigDecimal.ONE, new BigDecimal("3.00"), new BigDecimal("3.00"),
            BigDecimal.ONE, new BigDecimal("95.50"), true, null
        );
        return new GradeVerificationResponse(
            11L, "테스트 규칙", 2, "테스트대학교", "학생부교과", "컴퓨터공학과",
            new BigDecimal("95.50"), BigDecimal.ZERO, new BigDecimal("2.00"),
            SelectionStrategy.TOP_N_COURSES, ScoreAggregation.COURSE_SCORE_AVERAGE,
            "2027 모집요강.pdf", "12-13", 1, 0, summary, List.of(course),
            List.of("테스트 경고")
        );
    }
}
