package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportRowError;
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
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.stereotype.Component;

@Component
class TranscriptValidationExcelWriter {
    private static final String[] RESULT_HEADERS = {
        "지원정보 행", "수험번호", "전형명", "모집단위명",
        "등급×이수단위 합", "환산점수×이수단위 합", "총 반영 이수단위",
        "평균등급(고정밀도)", "평균등급(규칙 반올림)", "기준 환산점수", "전형별 교과 배율",
        "교과 반영점수(반올림 전)", "교과 반영점수"
    };
    private static final String[] SELECTED_COURSE_HEADERS = {
        "지원정보 행", "수험번호", "학생명", "전형명", "모집단위명", "졸업연도",
        "선택순번", "원본 성적 행", "고교코드", "고교명", "학년", "학기",
        "원본 교과", "적용 교과", "과목명", "석차등급", "성취도", "이수단위",
        "유효등급", "환산점수", "반영 이수단위", "적용 가중치", "가중점수",
        "진로선택", "전문교과", "석차", "동석차", "수강자수"
    };

    byte[] write(
        String originalFileName,
        String sourceFormat,
        int applicationRows,
        int totalRows,
        List<TranscriptImportRowError> skipped,
        List<TranscriptExcelRow> courses,
        List<TranscriptImportRowError> errors,
        List<String> warnings,
        TranscriptBatchVerificationResult verification
    ) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        workbook.setCompressTempFiles(true);
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            createVerificationResultSheet(workbook, styles, verification);
            createSelectedCourseSheet(workbook, styles, verification);
            createSummarySheet(
                workbook, styles, originalFileName, sourceFormat, applicationRows,
                totalRows, courses.size(), errors.size(), skipped.size(), warnings,
                verification, skipped, errors
            );
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "검증 결과 Excel 파일을 생성하지 못했습니다.");
        }
    }

    private void createSummarySheet(
        SXSSFWorkbook workbook,
        Styles styles,
        String originalFileName,
        String sourceFormat,
        int applicationRows,
        int totalRows,
        int validRows,
        int invalidRows,
        int skippedRows,
        List<String> warnings,
        TranscriptBatchVerificationResult verification,
        List<TranscriptImportRowError> skipped,
        List<TranscriptImportRowError> errors
    ) {
        Sheet sheet = workbook.createSheet("검증 요약");
        sheet.setDisplayGridlines(false);
        title(sheet, styles, "Excel 가져오기 검증 결과", 7);

        int rowIndex = 2;
        rowIndex = section(sheet, styles, rowIndex, "파일 정보");
        rowIndex = keyValue(sheet, styles, rowIndex, "파일명", originalFileName, "인식 형식", formatLabel(sourceFormat));
        rowIndex++;
        rowIndex = section(sheet, styles, rowIndex, "검증 집계");
        rowIndex = keyValue(sheet, styles, rowIndex, "지원정보", applicationRows, "전체 성적", totalRows);
        rowIndex = keyValue(sheet, styles, rowIndex, "정상", validRows, "제외", skippedRows);
        rowIndex = keyValue(sheet, styles, rowIndex, "오류", invalidRows, "DB 저장 가능", invalidRows == 0 ? "가능" : "저장 정책에 따름");
        rowIndex = keyValue(sheet, styles, rowIndex, "성적 검증 성공", verification.successes().size(),
            "성적 검증 실패", verification.failures().size());

        if (warnings != null && !warnings.isEmpty()) {
            rowIndex++;
            rowIndex = section(sheet, styles, rowIndex, "안내 사항");
            for (String warning : warnings) {
                Row row = sheet.createRow(rowIndex++);
                Cell cell = row.createCell(0);
                set(cell, warning, styles.warning);
                sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 7));
            }
        }

        if (!verification.failures().isEmpty()) {
            rowIndex++;
            rowIndex = section(sheet, styles, rowIndex, "성적 검증 실패 상세");
            rowIndex = tableHeader(sheet, styles, rowIndex, new String[] {
                "지원정보 행", "수험번호", "전형명", "모집단위명", "대상 과목 수", "실패 코드", "실패 사유"
            });
            for (TranscriptBatchVerificationResult.Failure failure : verification.failures()) {
                TransferApplicationRow application = failure.application();
                writeRow(sheet.createRow(rowIndex++), new Object[] {
                    application.rowNumber(), application.applicantNumber(), application.admissionTrackName(),
                    application.recruitmentUnitName(), failure.availableCourseCount(), failure.code(), failure.reason()
                }, styles, 5);
            }
        }
        rowIndex = appendImportIssues(sheet, styles, rowIndex, "가져오기 제외 상세", skipped, styles.warning);
        appendImportIssues(sheet, styles, rowIndex, "가져오기 오류 상세", errors, styles.error);

        int[] widths = {18, 24, 24, 28, 16, 22, 70, 18};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
        sheet.createFreezePane(0, 2);
    }

    private int appendImportIssues(
        Sheet sheet,
        Styles styles,
        int rowIndex,
        String label,
        List<TranscriptImportRowError> issues,
        CellStyle reasonStyle
    ) {
        if (issues == null || issues.isEmpty()) return rowIndex;
        rowIndex++;
        rowIndex = section(sheet, styles, rowIndex, label);
        rowIndex = tableHeader(sheet, styles, rowIndex, new String[] {"원본 행", "처리 사유"});
        for (TranscriptImportRowError issue : issues) {
            Row row = sheet.createRow(rowIndex++);
            set(row.createCell(0), issue.rowNumber(), styles.integer);
            set(row.createCell(1), issue.reason(), reasonStyle);
        }
        return rowIndex;
    }

    private int tableHeader(Sheet sheet, Styles styles, int rowIndex, String[] headers) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(28);
        for (int column = 0; column < headers.length; column++) {
            set(row.createCell(column), headers[column], styles.header);
        }
        return rowIndex + 1;
    }

    private void createVerificationResultSheet(
        SXSSFWorkbook workbook,
        Styles styles,
        TranscriptBatchVerificationResult verification
    ) {
        Sheet sheet = workbook.createSheet("학생별 검증 결과");
        sheet.setDisplayGridlines(false);
        title(sheet, styles, "한신대 교과성적 검증 결과 - 비교과·고사·학교폭력 미포함", RESULT_HEADERS.length - 1);
        header(sheet, styles, RESULT_HEADERS);
        List<TranscriptBatchVerificationResult.Success> results = new ArrayList<>(verification.successes());
        results.sort(Comparator.comparingInt(success -> success.application().rowNumber()));

        int rowIndex = 3;
        for (TranscriptBatchVerificationResult.Success success : results) {
            Row row = sheet.createRow(rowIndex++);
            TransferApplicationRow application = success.application();
            GradeVerificationResponse result = success.verification();
            GradeVerificationResponse.CalculationSummary summary = result.calculationSummary();
            BigDecimal preciseAverage = divide(
                summary.gradeTimesCreditsSum(), summary.totalIncludedCredits()
            );
            Object[] values = {
                application.rowNumber(), application.applicantNumber(), application.admissionTrackName(),
                application.recruitmentUnitName(), summary.gradeTimesCreditsSum(),
                summary.convertedScoreTimesCreditsSum(), summary.totalIncludedCredits(),
                preciseAverage, result.averageGrade(), result.baseScore(), summary.scoreMultiplier(),
                summary.scoreBeforeFinalRounding(), result.finalScore()
            };
            writeRow(row, values, styles, -1);
        }
        finishTable(sheet, results.size(), RESULT_HEADERS.length);
        setWidths(sheet, RESULT_HEADERS, Set.of(2, 3));
    }

    private void createSelectedCourseSheet(
        SXSSFWorkbook workbook,
        Styles styles,
        TranscriptBatchVerificationResult verification
    ) {
        Sheet sheet = workbook.createSheet("학생별 선택 과목");
        sheet.setDisplayGridlines(false);
        title(
            sheet, styles, "학생별 선택 과목 상세 - 실제 교과성적 계산에 반영된 과목",
            SELECTED_COURSE_HEADERS.length - 1
        );
        header(sheet, styles, SELECTED_COURSE_HEADERS);

        List<TranscriptBatchVerificationResult.Success> results = new ArrayList<>(verification.successes());
        results.sort(Comparator.comparingInt(success -> success.application().rowNumber()));
        int rowIndex = 3;
        for (TranscriptBatchVerificationResult.Success success : results) {
            List<TranscriptBatchVerificationResult.SelectedCourse> selectedCourses =
                new ArrayList<>(success.selectedCourses());
            selectedCourses.sort(selectedCourseComparator());
            for (int index = 0; index < selectedCourses.size(); index++) {
                TranscriptBatchVerificationResult.SelectedCourse selected = selectedCourses.get(index);
                TranscriptExcelRow source = selected.source();
                GradeVerificationResponse.CourseCalculation calculation = selected.calculation();
                TransferApplicationRow application = success.application();
                writeRow(sheet.createRow(rowIndex++), new Object[] {
                    application.rowNumber(), application.applicantNumber(), success.studentName(),
                    application.admissionTrackName(), application.recruitmentUnitName(),
                    application.graduationYear(), index + 1, source.rowNumber(),
                    source.highSchoolCode(), source.highSchoolName(), source.schoolYear(), source.semester(),
                    source.subjectCategory(), calculation.appliedSubjectCategory(), source.courseName(),
                    source.grade(), source.achievement(), source.credits(), calculation.effectiveGrade(),
                    calculation.convertedScore(), calculation.appliedCredits(), calculation.appliedWeight(),
                    calculation.weightedScore(), source.careerSubject() ? "Y" : "N",
                    source.professionalCourse() ? "Y" : "N", source.rankPosition(),
                    source.tiedRankCount(), source.studentCount()
                }, styles, -1);
            }
        }
        finishTable(sheet, rowIndex - 3, SELECTED_COURSE_HEADERS.length);
        setWidths(sheet, SELECTED_COURSE_HEADERS, Set.of(3, 4, 9, 14));
        sheet.setColumnWidth(2, 24 * 256);
    }

    private Comparator<TranscriptBatchVerificationResult.SelectedCourse> selectedCourseComparator() {
        return Comparator
            .comparing(
                (TranscriptBatchVerificationResult.SelectedCourse selected) ->
                    selected.calculation().effectiveGrade(),
                Comparator.nullsLast(Comparator.naturalOrder())
            )
            .thenComparing(
                selected -> selected.calculation().appliedCredits(),
                Comparator.nullsLast(Comparator.reverseOrder())
            )
            .thenComparing(
                selected -> selected.source().schoolYear(),
                Comparator.reverseOrder()
            )
            .thenComparing(
                selected -> selected.source().semester(),
                Comparator.reverseOrder()
            )
            .thenComparingInt(selected -> selected.source().rowNumber());
    }

    private void writeRow(Row row, Object[] values, Styles styles, int errorFromColumn) {
        for (int column = 0; column < values.length; column++) {
            Object value = values[column];
            CellStyle style;
            if ("성공".equals(value)) style = styles.success;
            else if ("실패".equals(value)) style = styles.error;
            else if (errorFromColumn >= 0 && column >= errorFromColumn) style = styles.error;
            else if (value instanceof Integer || value instanceof Long) style = styles.integer;
            else if (value instanceof Number) style = styles.decimal;
            else style = styles.text;
            set(row.createCell(column), value, style);
        }
    }

    private void finishTable(Sheet sheet, int dataRows, int columns) {
        sheet.createFreezePane(0, 3);
        if (dataRows > 0) {
            sheet.setAutoFilter(new CellRangeAddress(2, dataRows + 2, 0, columns - 1));
        }
    }

    private void setWidths(Sheet sheet, String[] headers, Set<Integer> wideColumns) {
        for (int column = 0; column < headers.length; column++) {
            int width = wideColumns.contains(column) ? 42 : Math.max(12, Math.min(22, headers[column].length() + 5));
            sheet.setColumnWidth(column, width * 256);
        }
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) return null;
        return numerator.divide(denominator, 12, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private void title(Sheet sheet, Styles styles, String value, int lastColumn) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(30);
        Cell cell = row.createCell(0);
        set(cell, value, styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastColumn));
    }

    private void header(Sheet sheet, Styles styles, String[] headers) {
        Row row = sheet.createRow(2);
        row.setHeightInPoints(28);
        for (int index = 0; index < headers.length; index++) {
            set(row.createCell(index), headers[index], styles.header);
        }
    }

    private int section(Sheet sheet, Styles styles, int rowIndex, String value) {
        Row row = sheet.createRow(rowIndex);
        set(row.createCell(0), value, styles.section);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 7));
        return rowIndex + 1;
    }

    private int keyValue(
        Sheet sheet,
        Styles styles,
        int rowIndex,
        String firstKey,
        Object firstValue,
        String secondKey,
        Object secondValue
    ) {
        Row row = sheet.createRow(rowIndex);
        set(row.createCell(0), firstKey, styles.key);
        set(row.createCell(1), firstValue, firstValue instanceof Integer || firstValue instanceof Long
            ? styles.integer : styles.value);
        set(row.createCell(2), secondKey, styles.key);
        set(row.createCell(3), secondValue, secondValue instanceof Integer || secondValue instanceof Long
            ? styles.integer : styles.value);
        return rowIndex + 1;
    }

    private void set(Cell cell, Object value, CellStyle style) {
        cell.setCellStyle(style);
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(value == null ? "" : value.toString());
    }

    private String formatLabel(String sourceFormat) {
        return "HANSHIN_MULTI_SHEET_V1".equals(sourceFormat) ? "한신대 전달양식" : "표준 성적양식";
    }

    private static final class Styles {
        private final CellStyle title;
        private final CellStyle section;
        private final CellStyle key;
        private final CellStyle value;
        private final CellStyle header;
        private final CellStyle text;
        private final CellStyle integer;
        private final CellStyle decimal;
        private final CellStyle warning;
        private final CellStyle error;
        private final CellStyle success;

        private Styles(SXSSFWorkbook workbook) {
            title = style(workbook, "174A37", IndexedColors.WHITE.getIndex(), true, 16);
            title.setAlignment(HorizontalAlignment.LEFT);
            section = style(workbook, "DDECE2", IndexedColors.DARK_GREEN.getIndex(), true, 11);
            key = bordered(workbook, "EEF5F0", IndexedColors.DARK_GREEN.getIndex(), true);
            value = bordered(workbook, null, IndexedColors.BLACK.getIndex(), false);
            header = bordered(workbook, "2E6849", IndexedColors.WHITE.getIndex(), true);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setWrapText(true);
            text = bordered(workbook, null, IndexedColors.BLACK.getIndex(), false);
            integer = bordered(workbook, null, IndexedColors.BLACK.getIndex(), false);
            integer.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            decimal = bordered(workbook, null, IndexedColors.BLACK.getIndex(), false);
            decimal.setDataFormat(workbook.createDataFormat().getFormat("0.######"));
            warning = style(workbook, "FFF4CC", IndexedColors.DARK_RED.getIndex(), false, 10);
            warning.setWrapText(true);
            error = bordered(workbook, "FCE8E6", IndexedColors.DARK_RED.getIndex(), false);
            error.setWrapText(true);
            success = bordered(workbook, "E5F3E8", IndexedColors.DARK_GREEN.getIndex(), true);
        }

        private static CellStyle style(
            SXSSFWorkbook workbook,
            String fill,
            short fontColor,
            boolean bold,
            int fontSize
        ) {
            XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            if (fill != null) {
                style.setFillForegroundColor(new XSSFColor(java.awt.Color.decode("#" + fill), null));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            Font font = workbook.createFont();
            font.setColor(fontColor);
            font.setBold(bold);
            font.setFontHeightInPoints((short) fontSize);
            style.setFont(font);
            return style;
        }

        private static CellStyle bordered(SXSSFWorkbook workbook, String fill, short fontColor, boolean bold) {
            CellStyle style = style(workbook, fill, fontColor, bold, 10);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            return style;
        }
    }
}
