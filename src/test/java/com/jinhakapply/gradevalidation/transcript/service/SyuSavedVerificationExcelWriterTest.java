package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse.CalculationSummary;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse.CourseCalculation;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationBatchResponse;
import com.jinhakapply.gradevalidation.transcript.repository.SavedVerificationQueryRepository;
import com.jinhakapply.gradevalidation.transcript.repository.SavedVerificationQueryRepository.ScenarioExportProjection;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SyuSavedVerificationExcelWriterTest {

    @Mock SavedVerificationQueryRepository repository;
    @Mock ObjectMapper objectMapper;

    @Test
    void serializesCompactExportSummaryForFutureFastExports() {
        ObjectMapper mapper = new ObjectMapper();
        SyuScenarioExportSummary expected = summary();

        String json = mapper.writeValueAsString(expected);

        assertThat(mapper.readValue(json, SyuScenarioExportSummary.class)).isEqualTo(expected);
    }

    @Test
    void writesScenarioSummariesToOneSheetWithoutCourseExpansion() throws Exception {
        LocalDateTime savedAt = LocalDateTime.of(2026, 8, 14, 10, 53, 54);
        SavedVerificationBatchResponse batch = new SavedVerificationBatchResponse(
            21L, 2L, "삼육대학교", 2027, "삼육대_2027학년도_데이터전달.xlsx",
            SyuSourceExcelStreamer.SOURCE_FORMAT, 2, savedAt
        );
        ScenarioExportProjection first = result(
            "10005001", "학교장추천", "일반학과(부)", "summary-json", null
        );
        ScenarioExportProjection second = result(
            "10005001", "농어촌", "약학과", null, "result-json"
        );
        SyuScenarioExportSummary summary = summary();
        GradeVerificationResponse verification = verification();
        when(objectMapper.readValue("summary-json", SyuScenarioExportSummary.class)).thenReturn(summary);
        when(objectMapper.readValue("result-json", GradeVerificationResponse.class)).thenReturn(verification);
        doAnswer(invocation -> {
            Consumer<ScenarioExportProjection> consumer = invocation.getArgument(1);
            consumer.accept(first);
            consumer.accept(second);
            return null;
        }).when(repository).streamScenarioExportResults(eq(21L), org.mockito.ArgumentMatchers.any());

        byte[] bytes = new SyuSavedVerificationExcelWriter(repository, objectMapper).write(batch);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetName(0)).isEqualTo(SyuSavedVerificationExcelWriter.sheetName());
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("수험번호");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).contains("가상 시나리오");
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("10005001");
            assertThat(sheet.getRow(5).getCell(1).getStringCellValue()).isEqualTo("학교장추천");
            assertThat(sheet.getRow(6).getCell(2).getStringCellValue()).isEqualTo("약학과");
            assertThat(sheet.getRow(5).getCell(3).getNumericCellValue()).isEqualTo(53);
            assertThat(sheet.getRow(5).getCell(6).getNumericCellValue()).isEqualTo(4125);
            assertThat(sheet.getRow(5).getCell(8).getNumericCellValue()).isEqualTo(97.5833333333);
            assertThat(sheet.getRow(5).getCell(15).getNumericCellValue()).isEqualTo(99.07826);
            assertThat(sheet.getRow(5).getCell(16).getNumericCellValue()).isEqualTo(990.7826);
            assertThat(sheet.getLastRowNum()).isEqualTo(6);
        }
        assertThat(SyuSavedVerificationExcelWriter.headers())
            .contains("환산점수×이수단위 합", "1-1 학기", "교과 기준점수(100점)")
            .doesNotContain("검증결과 ID", "학생명", "규칙명", "규칙 버전", "검증 시각");
        verify(repository).streamScenarioExportResults(eq(21L), org.mockito.ArgumentMatchers.any());
    }

    private ScenarioExportProjection result(
        String applicantNumber,
        String admissionTrack,
        String recruitmentUnit,
        String exportSummaryJson,
        String resultJson
    ) {
        return new ScenarioExportProjection(
            applicantNumber, admissionTrack, recruitmentUnit,
            37, 16, new BigDecimal("3.573913"), new BigDecimal("990.7826"),
            exportSummaryJson, resultJson
        );
    }

    private SyuScenarioExportSummary summary() {
        return new SyuScenarioExportSummary(
            new BigDecimal("4125"), new BigDecimal("42"),
            new BigDecimal("97.5833333333"), null, null, null, null, null,
            new BigDecimal("99.07826")
        );
    }

    private GradeVerificationResponse verification() {
        CalculationSummary summary = mock(CalculationSummary.class);
        when(summary.convertedScoreTimesCreditsSum()).thenReturn(new BigDecimal("4125"));
        when(summary.totalIncludedCredits()).thenReturn(new BigDecimal("42"));
        when(summary.intermediateScale()).thenReturn(10);
        when(summary.intermediateRounding()).thenReturn(RoundingMode.DOWN);

        CourseCalculation calculation = mock(CourseCalculation.class);
        when(calculation.included()).thenReturn(true);
        when(calculation.schoolYear()).thenReturn(1);
        when(calculation.semester()).thenReturn(1);
        when(calculation.weightedScore()).thenReturn(new BigDecimal("585.5"));
        when(calculation.appliedWeight()).thenReturn(new BigDecimal("6"));

        GradeVerificationResponse verification = mock(GradeVerificationResponse.class);
        when(verification.calculationSummary()).thenReturn(summary);
        when(verification.calculations()).thenReturn(List.of(calculation));
        when(verification.baseScore()).thenReturn(new BigDecimal("99.07826"));
        return verification;
    }
}
