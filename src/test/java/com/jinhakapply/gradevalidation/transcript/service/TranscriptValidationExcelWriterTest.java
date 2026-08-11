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
        TranscriptExcelRow unselectedCourse = new TranscriptExcelRow(
            13, "A-001", "테스트 학생", null, null, 2027,
            1, 2, SubjectCategory.ENGLISH, "영어", 5, GradeScale.NINE_LEVEL,
            AchievementLevel.B, new BigDecimal("72"), new BigDecimal("68"),
            new BigDecimal("10.2"), 100, 20, 1, null, new BigDecimal("2"), false, false
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
                )),
                List.of(),
                null
            )),
            List.of(new TranscriptBatchVerificationResult.Failure(
                failedApplication, "실패 학생", 11, "INVALID_EVALUATION_RULE",
                "반영 가능한 교과성적이 최소 12과목 이상이어야 합니다."
            ))
        );

        byte[] file = writer.write(
            "테스트.xlsx", "HANSHIN_MULTI_SHEET_V1", "한신대학교", 1, 3,
            List.of(new TranscriptImportRowError(14, "편제명 없음")),
            List.of(course, unselectedCourse), List.of(new TranscriptImportRowError(13, "학기가 올바르지 않습니다.")),
            List.of("제외 행이 있습니다."), batch
        );

        assertThat(file).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetName(0)).isEqualTo("학생별 검증 결과");
            assertThat(workbook.getSheetName(1)).isEqualTo("학생별 과목 비교");
            assertThat(workbook.getSheetName(2)).isEqualTo("검증 요약");
            Sheet resultSheet = workbook.getSheet("학생별 검증 결과");
            assertThat(resultSheet.getRow(2).getLastCellNum()).isEqualTo((short) 12);
            assertThat(resultSheet.getRow(2)).noneMatch(cell ->
                "평균등급(고정밀도)".equals(cell.getStringCellValue())
                    || "평균등급(규칙 반올림)".equals(cell.getStringCellValue())
            );
            assertThat(resultSheet.getRow(2).getCell(11).getStringCellValue())
                .isEqualTo("교과성적(1,000점 만점)");
            assertThat(workbook.getSheet("학생별 검증 결과").getRow(0).getCell(0).getStringCellValue())
                .contains("비교과·고사·학교폭력 미포함");
            assertThat(resultSheet.getRow(3).getCell(10).getNumericCellValue())
                .isEqualTo(530.36);
            assertThat(resultSheet.getRow(3).getCell(11).getNumericCellValue())
                .isEqualTo(982.14);
            assertThat(resultSheet.getRow(3).getCell(4).getNumericCellValue())
                .isEqualTo(117);
            assertThat(workbook.getSheet("학생별 검증 결과").getLastRowNum()).isEqualTo(3);
            Sheet comparisonSheet = workbook.getSheet("학생별 과목 비교");
            assertThat(comparisonSheet.getRow(2).getLastCellNum()).isEqualTo((short) 21);
            assertThat(comparisonSheet.getRow(2)).noneMatch(cell -> List.of(
                "등급제", "수강자수", "표준편차", "원점수", "과목평균", "석차", "동석차",
                "졸업연도", "고교코드", "고교명"
            ).contains(cell.getStringCellValue()));
            assertThat(comparisonSheet.getRow(2).getCell(5).getStringCellValue()).isEqualTo("반영 여부");
            assertThat(comparisonSheet.getRow(2).getCell(10).getStringCellValue()).isEqualTo("교과");
            assertThat(comparisonSheet.getRow(2).getCell(11).getStringCellValue()).isEqualTo("과목명");
            assertThat(comparisonSheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("A-001");
            assertThat(comparisonSheet.getRow(3).getCell(5).getStringCellValue()).isEqualTo("선택됨");
            assertThat(comparisonSheet.getRow(3).getCell(5).getCellStyle().getFillPattern())
                .isEqualTo(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            assertThat(comparisonSheet.getRow(3).getCell(6).getNumericCellValue()).isEqualTo(1);
            assertThat(comparisonSheet.getRow(3).getCell(7).getNumericCellValue()).isEqualTo(12);
            assertThat(comparisonSheet.getRow(3).getCell(11).getStringCellValue()).isEqualTo("국어");
            assertThat(comparisonSheet.getRow(3).getCell(12).getNumericCellValue()).isEqualTo(2);
            assertThat(comparisonSheet.getRow(3).getCell(15).getNumericCellValue()).isEqualTo(99);
            assertThat(comparisonSheet.getRow(3).getCell(16).getNumericCellValue()).isEqualTo(297);
            assertThat(comparisonSheet.getRow(4).getCell(5).getStringCellValue()).isEqualTo("미선택");
            assertThat(comparisonSheet.getRow(4).getCell(7).getNumericCellValue()).isEqualTo(13);
            assertThat(comparisonSheet.getRow(4).getCell(11).getStringCellValue()).isEqualTo("영어");
            assertThat(comparisonSheet.getLastRowNum()).isEqualTo(4);
            assertThat(workbook.getSheet("학생별 전체 과목")).isNull();
            assertThat(workbook.getSheet("학생별 선택 과목")).isNull();
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

    @Test
    void writesKbuTitleAndIntermediateCalculationSheet() throws Exception {
        TransferApplicationRow application = new TransferApplicationRow(
            2, 2026, "K-001", "01", "수시 일반고", "10", "(주)간호학과", 2026
        );
        TranscriptBatchVerificationResult batch = new TranscriptBatchVerificationResult(
            List.of(new TranscriptBatchVerificationResult.Success(
                application, "경복 학생", verification(), List.of(),
                List.of(
                    new TranscriptBatchVerificationResult.IntermediateCalculation(
                        "교과", "국어", true, 2, 8, new BigDecimal("24"),
                        new BigDecimal("48"), new BigDecimal("2"),
                        new BigDecimal("2352"), new BigDecimal("98")
                    ),
                    new TranscriptBatchVerificationResult.IntermediateCalculation(
                        "교과", "영어", false, null, 6, new BigDecimal("18"),
                        new BigDecimal("72"), new BigDecimal("4"),
                        new BigDecimal("1692"), new BigDecimal("94")
                    )
                ),
                null
            )),
            List.of()
        );

        byte[] file = writer.write(
            "경복대 성적검증 파일.xlsx", "KOREAN_MULTI_SHEET_V1", "경복대학교", 1, 2,
            List.of(), List.of(), List.of(), List.of(), batch
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(workbook.getSheet("학생별 검증 결과").getRow(0).getCell(0).getStringCellValue())
                .startsWith("경복대 교과성적 검증 결과");
            Sheet intermediate = workbook.getSheet("성적 산출 중간값");
            assertThat(intermediate).isNotNull();
            assertThat(intermediate.getRow(2).getCell(11).getStringCellValue()).isEqualTo("평균등급");
            assertThat(intermediate.getRow(3).getCell(5).getStringCellValue()).isEqualTo("국어");
            assertThat(intermediate.getRow(3).getCell(6).getStringCellValue()).isEqualTo("선택됨");
            assertThat(intermediate.getRow(3).getCell(6).getCellStyle().getFillPattern())
                .isEqualTo(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            assertThat(intermediate.getRow(3).getCell(11).getNumericCellValue()).isEqualTo(2);
            assertThat(intermediate.getRow(4).getCell(6).getStringCellValue()).isEqualTo("미선택");
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
            new BigDecimal("2.786"), new BigDecimal("98.214"), new BigDecimal("5.4"),
            new BigDecimal("530.3556"), 3, RoundingMode.HALF_UP, 2, RoundingMode.HALF_UP,
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
            new BigDecimal("530.36"), new BigDecimal("98.214"), new BigDecimal("2.786"),
            SelectionStrategy.TOP_N_COURSES, ScoreAggregation.COURSE_SCORE_AVERAGE,
            "2027 모집요강.pdf", "36-38", 12, 4, summary, List.of(calculation), List.of()
        );
    }
}
