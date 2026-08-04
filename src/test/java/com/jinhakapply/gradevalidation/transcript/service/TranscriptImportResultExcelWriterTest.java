package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;

import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportMode;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TranscriptImportResultExcelWriterTest {

    private final TranscriptImportResultExcelWriter writer = new TranscriptImportResultExcelWriter();

    @Test
    void writesCompletedSourceImportSummaryAsExcel() throws Exception {
        StudentTranscriptImport transcriptImport = StudentTranscriptImport.create(
            2027,
            "삼육대_2026학년도_데이터전달.xlsx",
            TranscriptImportMode.VALID_ROWS_ONLY,
            "abc123",
            909_603,
            909_603,
            0,
            "SYU_SOURCE_WORKBOOK_V1"
        );
        ReflectionTestUtils.setField(transcriptImport, "id", 5L);

        byte[] result = writer.write(transcriptImport);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = workbook.getSheet("가져오기 결과");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                .contains("환산점수 검증 결과가 아닙니다");
            assertThat(sheet.getRow(4).getCell(1).getNumericCellValue()).isEqualTo(5);
            assertThat(sheet.getRow(10).getCell(1).getNumericCellValue()).isEqualTo(909_603);
            assertThat(sheet.getRow(10).getCell(3).getNumericCellValue()).isEqualTo(909_603);
        }
    }
}
