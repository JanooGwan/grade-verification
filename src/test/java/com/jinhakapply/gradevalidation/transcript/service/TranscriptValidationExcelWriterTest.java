package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.ScoreAggregation;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportRowError;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class TranscriptValidationExcelWriterTest {
    private final TranscriptValidationExcelWriter writer = new TranscriptValidationExcelWriter();

    @Test
    void writesCondensedResultsAndIssueSummary() throws Exception {
        TranscriptExcelRow course = new TranscriptExcelRow(
            12, "A-001", "테스트 학생", null, null, 2027,
            1, 1, SubjectCategory.KOREAN, "국어", 2, GradeScale.NINE_LEVEL,
            AchievementLevel.A, new BigDecimal("92"), new BigDecimal("70"),
            new BigDecimal("12.5"), 100, 2, 1, null, new BigDecimal("3"), false, false
        );
        GradeVerificationResponse verification = verification();
        TransferApplicationRow application = new TransferApplicationRow(
            2, 2027, "A-001", "06", "학생부교과", "21", "컴퓨터공학과", 2027
        );
        TransferApplicationRow failedApplication = new TransferApplicationRow(
            3, 2027, "A-002", "06", "학생부교과", "22", "인공지능학과", 2027
        );
        TranscriptBatchVerificationResult batch = new TranscriptBatchVerificationResult(
            List.of(new TranscriptBatchVerificationResult.Success(
                application, "테스트 학생", verification,
                List.of(new TranscriptBatchVerificationResult.SelectedCourse(
                    course, verification.calculations().getFirst()
                ))
            )),
            List.of(new TranscriptBatchVerificationResult.Failure(
                failedApplication, "실패 학생", 11, "INVALID_EVALUATION_RULE",
                "반영 가능한 교과성적이 최소 12과목 이상이어야 합니다."
            ))
        );

        byte[] file = writer.write(
            "테스트.xlsx", "HANSHIN_MULTI_SHEET_V1", 1, 3,
            List.of(new TranscriptImportRowError(14, "편제명 없음")),
            List.of(course), List.of(new TranscriptImportRowError(13, "학기가 올바르지 않습니다.")),
            List.of("제외 행이 있습니다."), batch
        );

        assertThat(file).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetName(0)).isEqualTo("학생별 검증 결과");
            assertThat(workbook.getSheetName(1)).isEqualTo("학생별 선택 과목");
            assertThat(workbook.getSheetName(2)).isEqualTo("검증 요약");
            assertThat(workbook.getSheet("학생별 검증 결과").getRow(2).getLastCellNum()).isEqualTo((short) 13);
            assertThat(workbook.getSheet("학생별 검증 결과").getRow(2).getCell(12).getStringCellValue())
                .isEqualTo("교과 반영점수");
            assertThat(workbook.getSheet("학생별 검증 결과").getRow(0).getCell(0).getStringCellValue())
                .contains("비교과·고사·학교폭력 미포함");
            assertThat(workbook.getSheet("학생별 검증 결과").getRow(3).getCell(12).getNumericCellValue())
                .isEqualTo(982.14);
            assertThat(workbook.getSheet("학생별 검증 결과").getRow(3).getCell(4).getNumericCellValue())
                .isEqualTo(117);
            assertThat(workbook.getSheet("학생별 검증 결과").getRow(3).getCell(7).getNumericCellValue())
                .isCloseTo(2.785714285714, org.assertj.core.data.Offset.offset(0.000000000001));
            assertThat(workbook.getSheet("학생별 검증 결과").getRow(3).getCell(8).getNumericCellValue())
                .isEqualTo(2.786);
            assertThat(workbook.getSheet("학생별 검증 결과").getLastRowNum()).isEqualTo(3);
            Sheet selectedCourseSheet = workbook.getSheet("학생별 선택 과목");
            assertThat(selectedCourseSheet.getRow(2).getLastCellNum()).isEqualTo((short) 28);
            assertThat(selectedCourseSheet.getRow(2).getCell(14).getStringCellValue()).isEqualTo("과목명");
            assertThat(selectedCourseSheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("A-001");
            assertThat(selectedCourseSheet.getRow(3).getCell(6).getNumericCellValue()).isEqualTo(1);
            assertThat(selectedCourseSheet.getRow(3).getCell(14).getStringCellValue()).isEqualTo("국어");
            assertThat(selectedCourseSheet.getRow(3).getCell(18).getNumericCellValue()).isEqualTo(2);
            assertThat(selectedCourseSheet.getRow(3).getCell(19).getNumericCellValue()).isEqualTo(99);
            assertThat(selectedCourseSheet.getRow(3).getCell(22).getNumericCellValue()).isEqualTo(297);
            assertThat(workbook.getSheet("검증 요약").getRow(3).getCell(1).getStringCellValue())
                .isEqualTo("테스트.xlsx");
            Sheet summarySheet = workbook.getSheet("검증 요약");
            assertThat(containsCellValue(summarySheet, "INVALID_EVALUATION_RULE")).isTrue();
            assertThat(containsCellValue(summarySheet, "반영 가능한 교과성적이 최소 12과목 이상이어야 합니다."))
                .isTrue();
            assertThat(containsCellValue(summarySheet, "편제명 없음")).isTrue();
            assertThat(containsCellValue(summarySheet, "학기가 올바르지 않습니다.")).isTrue();
        }
    }

    private boolean containsCellValue(Sheet sheet, String expected) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                    && expected.equals(cell.getStringCellValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private GradeVerificationResponse verification() {
        var summary = new GradeVerificationResponse.CalculationSummary(
            "Σ(과목 환산점수 × 이수단위) ÷ Σ(이수단위) × 점수 배율",
            new BigDecimal("117"), new BigDecimal("4125"), new BigDecimal("117"),
            new BigDecimal("4125"), new BigDecimal("42"), new BigDecimal("42"),
            new BigDecimal("2.786"), new BigDecimal("98.214"), new BigDecimal("10"),
            new BigDecimal("982.140"), 3, RoundingMode.HALF_UP, 2, RoundingMode.HALF_UP,
            Map.of()
        );
        var calculation = new GradeVerificationResponse.CourseCalculation(
            "국어", 1, 1, SubjectCategory.KOREAN, SubjectCategory.KOREAN,
            2, GradeScale.NINE_LEVEL, AchievementLevel.A, 2, 1, 100,
            new BigDecimal("2"), null, new BigDecimal("2"), new BigDecimal("99"),
            BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("3"), new BigDecimal("3"),
            new BigDecimal("3"), new BigDecimal("297"), true, null
        );
        return new GradeVerificationResponse(
            1L, "한신대 상위 12과목", 1, "한신대학교", "학생부교과", "전체 모집단위",
            new BigDecimal("982.14"), new BigDecimal("98.214"), new BigDecimal("2.786"),
            SelectionStrategy.TOP_N_COURSES, ScoreAggregation.COURSE_SCORE_AVERAGE,
            "2027 모집요강.pdf", "36-38", 12, 4, summary, List.of(calculation), List.of()
        );
    }
}
