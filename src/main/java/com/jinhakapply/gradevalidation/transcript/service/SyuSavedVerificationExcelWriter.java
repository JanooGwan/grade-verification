package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationBatchResponse;
import com.jinhakapply.gradevalidation.transcript.repository.SavedVerificationQueryRepository;
import com.jinhakapply.gradevalidation.transcript.repository.SavedVerificationQueryRepository.ScenarioExportProjection;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
class SyuSavedVerificationExcelWriter {

    private static final String SHEET_NAME = "전형별 검증 결과";
    private static final int HEADER_ROW_INDEX = 4;
    private static final int DATA_ROW_INDEX = HEADER_ROW_INDEX + 1;
    private static final String[] HEADERS = {
        "수험번호", "전형", "모집단위", "전체 과목수", "반영 과목수", "제외 과목수",
        "환산점수×이수단위 합", "반영 이수단위 합",
        "반영 교과영역",
        "교과영역 1", "영역 1 성적(100점)", "교과영역 2", "영역 2 성적(100점)",
        "교과영역 3", "영역 3 성적(100점)", "교과영역 4", "영역 4 성적(100점)",
        "평균등급", "교과 기준점수(100점)", "교과 반영점수"
    };

    private final SavedVerificationQueryRepository repository;
    private final ObjectMapper objectMapper;

    byte[] write(SavedVerificationBatchResponse batch) {
        requireExcelRowCapacity(batch.resultCount());
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        workbook.setCompressTempFiles(true);
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            Styles styles = new Styles(workbook);
            configureSheet(sheet, styles, batch);
            int[] rowIndex = {DATA_ROW_INDEX};
            repository.streamScenarioExportResults(batch.sourceImportId(), result ->
                writeResultRow(sheet.createRow(rowIndex[0]++), styles, result)
            );
            sheet.setAutoFilter(new CellRangeAddress(
                HEADER_ROW_INDEX,
                Math.max(HEADER_ROW_INDEX, rowIndex[0] - 1),
                0,
                HEADERS.length - 1
            ));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "삼육대 저장 검증 결과 Excel 파일을 생성하지 못했습니다.");
        }
    }

    private void requireExcelRowCapacity(long resultCount) {
        long availableRows = SpreadsheetVersion.EXCEL2007.getMaxRows() - DATA_ROW_INDEX;
        if (resultCount > availableRows) {
            throw CustomException.of(
                INVALID_TRANSCRIPT_FILE,
                "저장 결과 %,d건이 Excel 단일 시트 최대 행 수를 초과합니다.".formatted(resultCount)
            );
        }
    }

    private void configureSheet(
        Sheet sheet,
        Styles styles,
        SavedVerificationBatchResponse batch
    ) {
        sheet.setDisplayGridlines(false);
        Row title = sheet.createRow(0);
        title.setHeightInPoints(32);
        set(title.createCell(0), "삼육대학교 전형별 저장 검증 결과", styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

        Row metadata = sheet.createRow(1);
        set(metadata.createCell(0), "원본 파일", styles.label);
        set(metadata.createCell(1), batch.originalFileName(), styles.text);
        set(metadata.createCell(3), "결과 건수", styles.label);
        set(metadata.createCell(4), batch.resultCount(), styles.integer);

        Row notice = sheet.createRow(2);
        notice.setHeightInPoints(34);
        set(notice.createCell(0),
            "실제 지원정보가 없어 게시된 규칙 각각으로 계산한 가상 시나리오입니다. "
                + "영역별 성적은 반영 과목 환산점수의 적용 이수단위 가중평균(100점 기준)입니다.",
            styles.notice);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, HEADERS.length - 1));

        Row header = sheet.createRow(HEADER_ROW_INDEX);
        header.setHeightInPoints(30);
        for (int index = 0; index < HEADERS.length; index++) {
            set(header.createCell(index), HEADERS[index], styles.header);
        }
        int[] widths = {
            16, 20, 22, 14, 14, 14, 24, 18, 22,
            14, 20, 14, 20, 14, 20, 14, 20,
            14, 22, 18
        };
        for (int index = 0; index < widths.length; index++) {
            sheet.setColumnWidth(index, widths[index] * 256);
        }
        sheet.createFreezePane(3, DATA_ROW_INDEX);
    }

    private void writeResultRow(Row row, Styles styles, ScenarioExportProjection result) {
        SyuScenarioExportSummary summary = exportSummary(result);
        set(row.createCell(0), result.applicantNumber(), styles.text);
        set(row.createCell(1), result.admissionTrackName(), styles.text);
        set(row.createCell(2), result.recruitmentUnitName(), styles.text);
        set(row.createCell(3), result.includedCourseCount() + result.excludedCourseCount(), styles.integer);
        set(row.createCell(4), result.includedCourseCount(), styles.integer);
        set(row.createCell(5), result.excludedCourseCount(), styles.integer);
        set(row.createCell(6), summary.convertedScoreTimesCreditsSum(), styles.decimal);
        set(row.createCell(7), summary.totalIncludedCredits(), styles.decimal);
        set(row.createCell(8), summary.domainNames(), styles.text);
        for (int index = 0; index < 4; index++) {
            SyuScenarioExportSummary.SubjectDomainScore domain = summary.domain(index);
            set(row.createCell(9 + index * 2), domain == null ? null : domain.domainName(), styles.text);
            set(row.createCell(10 + index * 2), domain == null ? null : domain.score(), styles.decimal);
        }
        set(row.createCell(17), result.averageGrade(), styles.decimal);
        set(row.createCell(18), summary.baseScore(), styles.decimal);
        set(row.createCell(19), result.finalScore(), styles.decimal);
    }

    private SyuScenarioExportSummary exportSummary(ScenarioExportProjection result) {
        if (result.exportSummaryJson() != null && !result.exportSummaryJson().isBlank()) {
            SyuScenarioExportSummary summary = objectMapper.readValue(
                result.exportSummaryJson(), SyuScenarioExportSummary.class
            );
            if (summary.subjectDomains() != null) return summary;
        }
        GradeVerificationResponse verification = objectMapper.readValue(
            result.resultJson(), GradeVerificationResponse.class
        );
        return SyuScenarioExportSummary.from(verification);
    }

    private void set(Cell cell, Object value, CellStyle style) {
        cell.setCellStyle(style);
        if (value == null) cell.setBlank();
        else if (value instanceof BigDecimal decimal) cell.setCellValue(decimal.doubleValue());
        else if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(value.toString());
    }

    static String sheetName() {
        return SHEET_NAME;
    }

    static java.util.List<String> headers() {
        return java.util.List.of(HEADERS);
    }

    private static final class Styles {
        private final CellStyle title;
        private final CellStyle label;
        private final CellStyle notice;
        private final CellStyle header;
        private final CellStyle text;
        private final CellStyle integer;
        private final CellStyle decimal;

        private Styles(SXSSFWorkbook workbook) {
            title = style(workbook, IndexedColors.DARK_GREEN, IndexedColors.WHITE, true);
            label = style(workbook, IndexedColors.LIGHT_GREEN, IndexedColors.DARK_GREEN, true);
            notice = style(workbook, IndexedColors.LIGHT_YELLOW, IndexedColors.DARK_RED, false);
            notice.setWrapText(true);
            header = style(workbook, IndexedColors.DARK_GREEN, IndexedColors.WHITE, true);
            text = style(workbook, IndexedColors.WHITE, IndexedColors.BLACK, false);
            integer = style(workbook, IndexedColors.WHITE, IndexedColors.BLACK, false);
            integer.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            decimal = style(workbook, IndexedColors.WHITE, IndexedColors.BLACK, false);
            decimal.setDataFormat(workbook.createDataFormat().getFormat("#,##0.########"));
        }

        private static CellStyle style(
            SXSSFWorkbook workbook,
            IndexedColors fill,
            IndexedColors fontColor,
            boolean bold
        ) {
            CellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(fill.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setBorderBottom(BorderStyle.THIN);
            Font font = workbook.createFont();
            font.setBold(bold);
            font.setColor(fontColor.getIndex());
            style.setFont(font);
            return style;
        }
    }
}
