package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportRowError;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class TranscriptValidationExcelWriterTest {
    private final TranscriptValidationExcelWriter writer = new TranscriptValidationExcelWriter();

    @Test
    void writesSummaryValidCoursesAndErrors() throws Exception {
        TranscriptExcelRow course = new TranscriptExcelRow(
            12, "A-001", "테스트 학생", null, null, 2027,
            1, 1, SubjectCategory.KOREAN, "국어", 2, GradeScale.NINE_LEVEL,
            AchievementLevel.A, new BigDecimal("92"), new BigDecimal("70"),
            new BigDecimal("12.5"), 100, 2, 1, null, new BigDecimal("3"), false, false
        );

        byte[] file = writer.write(
            "테스트.xlsx", "HANSHIN_MULTI_SHEET_V1", 1, 3,
            List.of(new TranscriptImportRowError(14, "편제명 없음")),
            List.of(course), List.of(new TranscriptImportRowError(13, "학기가 올바르지 않습니다.")),
            List.of("제외 행이 있습니다.")
        );

        assertThat(file).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(workbook.getSheetName(0)).isEqualTo("검증 요약");
            assertThat(workbook.getSheetName(1)).isEqualTo("정상 과목");
            assertThat(workbook.getSheetName(2)).isEqualTo("제외 행");
            assertThat(workbook.getSheetName(3)).isEqualTo("오류 행");
            assertThat(workbook.getSheet("검증 요약").getRow(3).getCell(1).getStringCellValue())
                .isEqualTo("테스트.xlsx");
            assertThat(workbook.getSheet("정상 과목").getRow(3).getCell(6).getStringCellValue())
                .isEqualTo("국어");
            assertThat(workbook.getSheet("오류 행").getRow(3).getCell(1).getStringCellValue())
                .isEqualTo("학기가 올바르지 않습니다.");
        }
    }
}
