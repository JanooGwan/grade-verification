package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationBatchResponse;
import com.jinhakapply.gradevalidation.transcript.repository.SavedVerificationQueryRepository;
import com.jinhakapply.gradevalidation.transcript.repository.SavedVerificationQueryRepository.ScenarioExportProjection;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyuSavedVerificationExcelWriterTest {

    @Mock SavedVerificationQueryRepository repository;

    @Test
    void writesScenarioSummariesToOneSheetWithoutCourseExpansion() throws Exception {
        LocalDateTime savedAt = LocalDateTime.of(2026, 8, 14, 10, 53, 54);
        SavedVerificationBatchResponse batch = new SavedVerificationBatchResponse(
            21L, 2L, "삼육대학교", 2027, "삼육대_2027학년도_데이터전달.xlsx",
            SyuSourceExcelStreamer.SOURCE_FORMAT, 2, savedAt
        );
        ScenarioExportProjection first = result(91L, "10005001", "학교장추천", "일반학과(부)", savedAt);
        ScenarioExportProjection second = result(92L, "10005001", "농어촌", "약학과", savedAt);
        doAnswer(invocation -> {
            Consumer<ScenarioExportProjection> consumer = invocation.getArgument(1);
            consumer.accept(first);
            consumer.accept(second);
            return null;
        }).when(repository).streamScenarioExportResults(eq(21L), org.mockito.ArgumentMatchers.any());

        byte[] bytes = new SyuSavedVerificationExcelWriter(repository).write(batch);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetName(0)).isEqualTo(SyuSavedVerificationExcelWriter.sheetName());
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("수험번호");
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("10005001");
            assertThat(sheet.getRow(4).getCell(3).getStringCellValue()).isEqualTo("학교장추천");
            assertThat(sheet.getRow(5).getCell(4).getStringCellValue()).isEqualTo("약학과");
            assertThat(sheet.getLastRowNum()).isEqualTo(5);
        }
        verify(repository).streamScenarioExportResults(eq(21L), org.mockito.ArgumentMatchers.any());
    }

    private ScenarioExportProjection result(
        Long id,
        String applicantNumber,
        String admissionTrack,
        String recruitmentUnit,
        LocalDateTime savedAt
    ) {
        return new ScenarioExportProjection(
            id, applicantNumber, "미등록", admissionTrack, recruitmentUnit,
            "2027 " + admissionTrack + " " + recruitmentUnit + " 교과성적",
            2, 37, 16, new BigDecimal("3.573913"), new BigDecimal("990.7826"), savedAt
        );
    }
}
