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
    private static final String[] COURSE_HEADERS = {
        "원본 행", "수험번호", "학생명", "학년", "학기", "교과", "과목명", "석차등급", "등급제",
        "성취도", "원점수", "과목평균", "표준편차", "수강자 수", "석차", "동석차 인원",
        "구 교육과정 평어", "이수단위", "진로선택", "전문교과"
    };
    private static final String[] RESULT_HEADERS = {
        "지원정보 행", "수험번호", "학생명", "전형코드", "전형명", "모집단위코드", "모집단위명",
        "검증 상태", "규칙 ID", "규칙명", "규칙 버전", "반영 과목 수", "제외 과목 수",
        "등급×이수단위 합", "환산점수×이수단위 합", "총 반영 이수단위",
        "등급×적용가중치 합", "환산점수×적용가중치 합", "총 적용가중치",
        "평균등급(고정밀도)", "평균등급(규칙 반올림)", "기준 환산점수", "점수 배율",
        "최종점수(반올림 전)", "최종 환산점수", "중간 반올림", "최종 반올림",
        "계산식", "주의 사항", "실패 코드", "실패 사유"
    };
    private static final String[] SELECTED_HEADERS = {
        "지원정보 행", "원본 성적 행", "수험번호", "전형명", "모집단위명", "학년", "학기",
        "교과", "과목명", "석차등급", "유효등급", "과목 환산점수", "이수단위", "적용 이수단위",
        "학년 가중치", "교과 가중치", "적용 가중치", "가중 환산점수"
    };
    private static final String[] FAILURE_HEADERS = {
        "지원정보 행", "수험번호", "학생명", "전형명", "모집단위명", "대상 과목 수", "실패 코드", "실패 사유"
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
            createSelectedCourseSheet(workbook, styles, verification.successes());
            createVerificationFailureSheet(workbook, styles, verification.failures());
            createSummarySheet(
                workbook, styles, originalFileName, sourceFormat, applicationRows,
                totalRows, courses.size(), errors.size(), skipped.size(), warnings,
                verification.successes().size(), verification.failures().size()
            );
            createCourseSheet(workbook, styles, courses);
            createIssueSheet(workbook, styles, "제외 행", "가져오기 제외 행", skipped);
            createErrorSheet(workbook, styles, errors);
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
        int verificationSuccesses,
        int verificationFailures
    ) {
        Sheet sheet = workbook.createSheet("검증 요약");
        sheet.setDisplayGridlines(false);
        title(sheet, styles, "Excel 가져오기 검증 결과", 3);

        int rowIndex = 2;
        rowIndex = section(sheet, styles, rowIndex, "파일 정보");
        rowIndex = keyValue(sheet, styles, rowIndex, "파일명", originalFileName, "인식 형식", formatLabel(sourceFormat));
        rowIndex++;
        rowIndex = section(sheet, styles, rowIndex, "검증 집계");
        rowIndex = keyValue(sheet, styles, rowIndex, "지원정보", applicationRows, "전체 성적", totalRows);
        rowIndex = keyValue(sheet, styles, rowIndex, "정상", validRows, "제외", skippedRows);
        rowIndex = keyValue(sheet, styles, rowIndex, "오류", invalidRows, "DB 저장 가능", invalidRows == 0 ? "가능" : "저장 정책에 따름");
        rowIndex = keyValue(sheet, styles, rowIndex, "성적 검증 성공", verificationSuccesses, "성적 검증 실패", verificationFailures);

        if (warnings != null && !warnings.isEmpty()) {
            rowIndex++;
            rowIndex = section(sheet, styles, rowIndex, "안내 사항");
            for (String warning : warnings) {
                Row row = sheet.createRow(rowIndex++);
                Cell cell = row.createCell(0);
                set(cell, warning, styles.warning);
                sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 3));
            }
        }
        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 42 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        sheet.setColumnWidth(3, 28 * 256);
        sheet.createFreezePane(0, 2);
    }

    private void createVerificationResultSheet(
        SXSSFWorkbook workbook,
        Styles styles,
        TranscriptBatchVerificationResult verification
    ) {
        Sheet sheet = workbook.createSheet("학생별 검증 결과");
        sheet.setDisplayGridlines(false);
        title(sheet, styles, "지원정보별 한신대 성적 검증 결과", RESULT_HEADERS.length - 1);
        header(sheet, styles, RESULT_HEADERS);
        List<Object> results = new ArrayList<>();
        results.addAll(verification.successes());
        results.addAll(verification.failures());
        results.sort(Comparator.comparingInt(this::applicationRowNumber));

        int rowIndex = 3;
        for (Object item : results) {
            Row row = sheet.createRow(rowIndex++);
            if (item instanceof TranscriptBatchVerificationResult.Failure failure) {
                TransferApplicationRow application = failure.application();
                Object[] values = {
                    application.rowNumber(), application.applicantNumber(), failure.studentName(),
                    application.admissionTrackCode(), application.admissionTrackName(),
                    application.recruitmentUnitCode(), application.recruitmentUnitName(), "실패",
                    null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, failure.code(), failure.reason()
                };
                writeRow(row, values, styles, RESULT_HEADERS.length - 2);
                continue;
            }
            TranscriptBatchVerificationResult.Success success =
                (TranscriptBatchVerificationResult.Success) item;
            TransferApplicationRow application = success.application();
            GradeVerificationResponse result = success.verification();
            GradeVerificationResponse.CalculationSummary summary = result.calculationSummary();
            BigDecimal preciseAverage = divide(
                summary.gradeTimesWeightSum(), summary.totalAppliedWeight()
            );
            Object[] values = {
                application.rowNumber(), application.applicantNumber(), success.studentName(),
                application.admissionTrackCode(), application.admissionTrackName(),
                application.recruitmentUnitCode(), application.recruitmentUnitName(), "성공",
                result.ruleId(), result.ruleName(), result.ruleVersion(), result.includedCourseCount(),
                result.excludedCourseCount(), summary.gradeTimesCreditsSum(),
                summary.convertedScoreTimesCreditsSum(), summary.totalIncludedCredits(),
                summary.gradeTimesWeightSum(), summary.convertedScoreTimesWeightSum(),
                summary.totalAppliedWeight(), preciseAverage, result.averageGrade(), result.baseScore(),
                summary.scoreMultiplier(), summary.scoreBeforeFinalRounding(), result.finalScore(),
                rounding(summary.intermediateScale(), summary.intermediateRounding()),
                rounding(summary.finalScale(), summary.finalRounding()), summary.formula(),
                String.join(" | ", result.warnings()), null, null
            };
            writeRow(row, values, styles, -1);
        }
        finishTable(sheet, results.size(), RESULT_HEADERS.length);
        setWidths(sheet, RESULT_HEADERS, Set.of(9, 27, 28, 30));
    }

    private int applicationRowNumber(Object result) {
        if (result instanceof TranscriptBatchVerificationResult.Success success) {
            return success.application().rowNumber();
        }
        return ((TranscriptBatchVerificationResult.Failure) result).application().rowNumber();
    }

    private void createSelectedCourseSheet(
        SXSSFWorkbook workbook,
        Styles styles,
        List<TranscriptBatchVerificationResult.Success> successes
    ) {
        Sheet sheet = workbook.createSheet("상위 12과목 상세");
        sheet.setDisplayGridlines(false);
        title(sheet, styles, "지원정보별 반영 과목 상세", SELECTED_HEADERS.length - 1);
        header(sheet, styles, SELECTED_HEADERS);
        int rowIndex = 3;
        for (TranscriptBatchVerificationResult.Success success : successes) {
            for (TranscriptBatchVerificationResult.SelectedCourse selected : success.selectedCourses()) {
                TransferApplicationRow application = success.application();
                TranscriptExcelRow source = selected.source();
                GradeVerificationResponse.CourseCalculation calculation = selected.calculation();
                Object[] values = {
                    application.rowNumber(), source.rowNumber(), application.applicantNumber(),
                    application.admissionTrackName(), application.recruitmentUnitName(), source.schoolYear(),
                    source.semester(), value(calculation.appliedSubjectCategory()), calculation.courseName(),
                    calculation.grade(), calculation.effectiveGrade(), calculation.convertedScore(),
                    calculation.credits(), calculation.appliedCredits(), calculation.gradeWeight(),
                    calculation.subjectWeight(), calculation.appliedWeight(), calculation.weightedScore()
                };
                writeRow(sheet.createRow(rowIndex++), values, styles, -1);
            }
        }
        finishTable(sheet, rowIndex - 3, SELECTED_HEADERS.length);
        setWidths(sheet, SELECTED_HEADERS, Set.of(3, 4, 8));
    }

    private void createVerificationFailureSheet(
        SXSSFWorkbook workbook,
        Styles styles,
        List<TranscriptBatchVerificationResult.Failure> failures
    ) {
        Sheet sheet = workbook.createSheet("검증 실패");
        sheet.setDisplayGridlines(false);
        title(sheet, styles, "성적 검증 실패 지원정보", FAILURE_HEADERS.length - 1);
        header(sheet, styles, FAILURE_HEADERS);
        int rowIndex = 3;
        for (TranscriptBatchVerificationResult.Failure failure : failures) {
            TransferApplicationRow application = failure.application();
            Object[] values = {
                application.rowNumber(), application.applicantNumber(), failure.studentName(),
                application.admissionTrackName(), application.recruitmentUnitName(), failure.availableCourseCount(),
                failure.code(), failure.reason()
            };
            writeRow(sheet.createRow(rowIndex++), values, styles, 6);
        }
        finishTable(sheet, failures.size(), FAILURE_HEADERS.length);
        setWidths(sheet, FAILURE_HEADERS, Set.of(3, 4, 7));
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

    private String rounding(int scale, RoundingMode mode) {
        return "소수 " + scale + "자리 / " + mode;
    }

    private void createCourseSheet(SXSSFWorkbook workbook, Styles styles, List<TranscriptExcelRow> courses) {
        Sheet sheet = workbook.createSheet("정상 과목");
        sheet.setDisplayGridlines(false);
        title(sheet, styles, "정상 처리 대상 과목", COURSE_HEADERS.length - 1);
        header(sheet, styles, COURSE_HEADERS);

        int rowIndex = 3;
        for (TranscriptExcelRow course : courses) {
            Row row = sheet.createRow(rowIndex++);
            Object[] values = {
                course.rowNumber(), course.applicantNumber(), course.studentName(), course.schoolYear(),
                course.semester(), value(course.subjectCategory()), course.courseName(), course.grade(),
                value(course.gradeScale()), value(course.achievement()), course.rawScore(), course.meanScore(),
                course.standardDeviation(), course.studentCount(), course.rankPosition(), course.tiedRankCount(),
                value(course.legacyAchievement()), course.credits(), course.careerSubject() ? "Y" : "N",
                course.professionalCourse() ? "Y" : "N"
            };
            for (int column = 0; column < values.length; column++) {
                CellStyle style = values[column] instanceof Integer || values[column] instanceof Long
                    ? styles.integer : values[column] instanceof Number ? styles.decimal : styles.text;
                set(row.createCell(column), values[column], style);
            }
        }
        sheet.createFreezePane(0, 3);
        if (!courses.isEmpty()) {
            sheet.setAutoFilter(new CellRangeAddress(2, rowIndex - 1, 0, COURSE_HEADERS.length - 1));
        }
        setCourseWidths(sheet);
    }

    private void createErrorSheet(
        SXSSFWorkbook workbook,
        Styles styles,
        List<TranscriptImportRowError> errors
    ) {
        createIssueSheet(workbook, styles, "오류 행", "가져오기 오류 행", errors);
    }

    private void createIssueSheet(
        SXSSFWorkbook workbook,
        Styles styles,
        String sheetName,
        String title,
        List<TranscriptImportRowError> errors
    ) {
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.setDisplayGridlines(false);
        title(sheet, styles, title, 1);
        header(sheet, styles, new String[] {"원본 행", "처리 사유"});
        int rowIndex = 3;
        for (TranscriptImportRowError error : errors) {
            Row row = sheet.createRow(rowIndex++);
            set(row.createCell(0), error.rowNumber(), styles.integer);
            set(row.createCell(1), error.reason(), "오류 행".equals(sheetName) ? styles.error : styles.warning);
        }
        sheet.createFreezePane(0, 3);
        if (!errors.isEmpty()) {
            sheet.setAutoFilter(new CellRangeAddress(2, rowIndex - 1, 0, 1));
        }
        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 70 * 256);
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
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 3));
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

    private void setCourseWidths(Sheet sheet) {
        for (int column = 0; column < COURSE_HEADERS.length; column++) {
            int width = switch (column) {
                case 1 -> 18;
                case 2 -> 16;
                case 5, 6 -> 20;
                default -> 13;
            };
            sheet.setColumnWidth(column, width * 256);
        }
    }

    private void set(Cell cell, Object value, CellStyle style) {
        cell.setCellStyle(style);
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(value == null ? "" : value.toString());
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
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
