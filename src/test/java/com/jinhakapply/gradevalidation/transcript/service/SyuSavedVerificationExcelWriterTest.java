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

import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
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
            "10005001", "학교장추천", "아트앤디자인학과", "summary-json", null
        );
        ScenarioExportProjection second = result(
            "10005001", "특성화고교", "일반학과(부)", null, "result-json"
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
            assertThat(sheet.getRow(6).getCell(2).getStringCellValue()).isEqualTo("일반학과(부)");
            assertThat(sheet.getRow(5).getCell(3).getNumericCellValue()).isEqualTo(53);
            assertThat(sheet.getRow(5).getCell(6).getNumericCellValue()).isEqualTo(4125);
            assertThat(sheet.getRow(5).getCell(8).getStringCellValue()).isEqualTo("영어, 국어");
            assertThat(sheet.getRow(5).getCell(9).getStringCellValue()).isEqualTo("영어");
            assertThat(sheet.getRow(5).getCell(10).getNumericCellValue()).isEqualTo(99.2);
            assertThat(sheet.getRow(5).getCell(11).getStringCellValue()).isEqualTo("국어");
            assertThat(sheet.getRow(5).getCell(12).getNumericCellValue()).isEqualTo(98.5);
            assertThat(sheet.getRow(6).getCell(8).getStringCellValue()).isEqualTo("국어, 영어, 수학");
            assertThat(sheet.getRow(6).getCell(9).getStringCellValue()).isEqualTo("국어");
            assertThat(sheet.getRow(6).getCell(11).getStringCellValue()).isEqualTo("영어");
            assertThat(sheet.getRow(6).getCell(13).getStringCellValue()).isEqualTo("수학");
            assertThat(sheet.getRow(6).getCell(15).getStringCellValue()).isEmpty();
            assertThat(sheet.getRow(5).getCell(18).getNumericCellValue()).isEqualTo(99.07826);
            assertThat(sheet.getRow(5).getCell(19).getNumericCellValue()).isEqualTo(990.7826);
            assertThat(sheet.getLastRowNum()).isEqualTo(6);
        }
        assertThat(SyuSavedVerificationExcelWriter.headers())
            .contains("환산점수×이수단위 합", "반영 교과영역", "영역 1 성적(100점)",
                "교과 기준점수(100점)")
            .doesNotContain("1-1 학기", "1-2 학기", "2-1 학기", "2-2 학기", "3-1 학기", "3-2 학기",
                "검증결과 ID", "학생명", "규칙명", "규칙 버전", "검증 시각");
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
            null, null, null, null, null, null,
            List.of(
                new SyuScenarioExportSummary.SubjectDomainScore(1, "영어", new BigDecimal("99.2")),
                new SyuScenarioExportSummary.SubjectDomainScore(2, "국어", new BigDecimal("98.5"))
            ),
            new BigDecimal("99.07826")
        );
    }

    private GradeVerificationResponse verification() {
        CalculationSummary summary = mock(CalculationSummary.class);
        when(summary.convertedScoreTimesCreditsSum()).thenReturn(new BigDecimal("4125"));
        when(summary.totalIncludedCredits()).thenReturn(new BigDecimal("42"));
        when(summary.intermediateScale()).thenReturn(10);
        when(summary.intermediateRounding()).thenReturn(RoundingMode.DOWN);

        GradeVerificationResponse verification = mock(GradeVerificationResponse.class);
        when(verification.calculationSummary()).thenReturn(summary);
        List<CourseCalculation> calculations = List.of(
            calculation(SubjectCategory.KOREAN, true, "294", "3"),
            calculation(SubjectCategory.ENGLISH, true, "396", "4"),
            calculation(SubjectCategory.MATH, true, "194", "2"),
            calculation(SubjectCategory.SOCIAL, false, "0", "3")
        );
        when(verification.calculations()).thenReturn(calculations);
        when(verification.selectionStrategy()).thenReturn(SelectionStrategy.ALL_COURSES);
        when(verification.baseScore()).thenReturn(new BigDecimal("99.07826"));
        return verification;
    }

    private CourseCalculation calculation(
        SubjectCategory category,
        boolean included,
        String weightedScore,
        String appliedWeight
    ) {
        CourseCalculation calculation = mock(CourseCalculation.class);
        when(calculation.included()).thenReturn(included);
        if (included) {
            when(calculation.appliedSubjectCategory()).thenReturn(category);
            when(calculation.weightedScore()).thenReturn(new BigDecimal(weightedScore));
            when(calculation.appliedWeight()).thenReturn(new BigDecimal(appliedWeight));
        }
        return calculation;
    }
}
