package com.jinhakapply.gradevalidation.admission.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.VERIFICATION_RESULT_EXPORT_FAILED;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
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
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class VerificationResultExcelWriter {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] COURSE_HEADERS = {
        "반영 여부", "제외 사유", "학년", "학기", "원본 교과", "적용 교과", "과목명", "등급제",
        "석차등급", "성취도", "석차", "동석차", "수강자 수", "석차 백분율", "구 교육과정 평어",
        "유효 등급", "환산점수", "학년 가중치", "교과 가중치", "이수단위", "적용 이수단위",
        "적용 가중치", "가중점수"
    };

    public byte[] write(
        String applicantNumber,
        String studentName,
        LocalDateTime verifiedAt,
        GradeVerificationResponse result
    ) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            createSummarySheet(workbook, styles, applicantNumber, studentName, verifiedAt, result);
            createCourseSheet(workbook, styles, result.calculations());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw CustomException.of(VERIFICATION_RESULT_EXPORT_FAILED);
        }
    }

    private void createSummarySheet(
        XSSFWorkbook workbook,
        Styles styles,
        String applicantNumber,
        String studentName,
        LocalDateTime verifiedAt,
        GradeVerificationResponse result
    ) {
        Sheet sheet = workbook.createSheet("검증 요약");
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(0, 2);

        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("성적 검증 결과");
        titleCell.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

        int rowIndex = 2;
        rowIndex = section(sheet, styles, rowIndex, "지원자 정보");
        rowIndex = keyValueRow(sheet, styles, rowIndex, "수험번호", applicantNumber, "성명", studentName);
        rowIndex = keyValueRow(sheet, styles, rowIndex, "검증 일시", format(verifiedAt), "대학", result.universityName());
        rowIndex = keyValueRow(sheet, styles, rowIndex, "전형", result.admissionType(), "모집단위", result.recruitmentUnit());

        rowIndex++;
        rowIndex = section(sheet, styles, rowIndex, "검증 결과");
        rowIndex = keyValueRow(sheet, styles, rowIndex, "최종 점수", result.finalScore(), "평균 등급", result.averageGrade());
        rowIndex = keyValueRow(sheet, styles, rowIndex, "반영 과목 수", result.includedCourseCount(), "제외 과목 수", result.excludedCourseCount());
        rowIndex = keyValueRow(sheet, styles, rowIndex, "선택 전략", value(result.selectionStrategy()), "점수 집계", value(result.scoreAggregation()));

        rowIndex++;
        rowIndex = section(sheet, styles, rowIndex, "적용 규칙");
        rowIndex = keyValueRow(sheet, styles, rowIndex, "규칙", result.ruleName(), "버전", result.ruleVersion());
        rowIndex = keyValueRow(sheet, styles, rowIndex, "근거 문서", result.sourceDocument(), "근거 페이지", result.sourcePages());

        GradeVerificationResponse.CalculationSummary summary = result.calculationSummary();
        if (summary != null) {
            rowIndex++;
            rowIndex = section(sheet, styles, rowIndex, "계산 요약");
            rowIndex = keyValueRow(sheet, styles, rowIndex, "계산식", summary.formula(), "최종 반올림 전 점수", summary.scoreBeforeFinalRounding());
            rowIndex = keyValueRow(sheet, styles, rowIndex, "등급×이수단위 합", summary.gradeTimesCreditsSum(), "환산점수×이수단위 합", summary.convertedScoreTimesCreditsSum());
            rowIndex = keyValueRow(sheet, styles, rowIndex, "등급×가중치 합", summary.gradeTimesWeightSum(), "환산점수×가중치 합", summary.convertedScoreTimesWeightSum());
            rowIndex = keyValueRow(sheet, styles, rowIndex, "총 적용 가중치", summary.totalAppliedWeight(), "총 반영 이수단위", summary.totalIncludedCredits());
            rowIndex = keyValueRow(sheet, styles, rowIndex, "기준 점수", summary.baseScore(), "점수 배율", summary.scoreMultiplier());
            rowIndex = keyValueRow(sheet, styles, rowIndex, "중간 반올림", rounding(summary.intermediateScale(), summary.intermediateRounding()), "최종 반올림", rounding(summary.finalScale(), summary.finalRounding()));
            if (summary.yearWeightDenominators() != null && !summary.yearWeightDenominators().isEmpty()) {
                rowIndex = keyValueRow(sheet, styles, rowIndex, "학년별 가중치 분모", yearWeights(summary.yearWeightDenominators()), "", "");
            }
        }

        List<String> warnings = result.warnings() == null ? List.of() : result.warnings();
        if (!warnings.isEmpty()) {
            rowIndex++;
            rowIndex = section(sheet, styles, rowIndex, "주의 사항");
            for (String warning : warnings) {
                Row row = sheet.createRow(rowIndex++);
                Cell cell = row.createCell(0);
                cell.setCellValue(warning);
                cell.setCellStyle(styles.warning);
                sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 3));
            }
        }

        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 34 * 256);
        sheet.setColumnWidth(2, 22 * 256);
        sheet.setColumnWidth(3, 34 * 256);
        sheet.setPrintGridlines(false);
    }

    private void createCourseSheet(
        XSSFWorkbook workbook,
        Styles styles,
        List<GradeVerificationResponse.CourseCalculation> calculations
    ) {
        Sheet sheet = workbook.createSheet("과목별 결과");
        sheet.setDisplayGridlines(false);

        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("과목별 성적 검증 결과");
        titleCell.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, COURSE_HEADERS.length - 1));

        Row headerRow = sheet.createRow(2);
        headerRow.setHeightInPoints(28);
        for (int column = 0; column < COURSE_HEADERS.length; column++) {
            Cell cell = headerRow.createCell(column);
            cell.setCellValue(COURSE_HEADERS[column]);
            cell.setCellStyle(styles.header);
        }

        List<GradeVerificationResponse.CourseCalculation> rows = calculations == null ? List.of() : calculations;
        int rowIndex = 3;
        for (GradeVerificationResponse.CourseCalculation course : rows) {
            Row row = sheet.createRow(rowIndex++);
            int column = 0;
            set(row.createCell(column++), course.included() ? "반영" : "제외", course.included() ? styles.included : styles.excluded);
            set(row.createCell(column++), course.exclusionReason(), styles.text);
            set(row.createCell(column++), course.schoolYear(), styles.integer);
            set(row.createCell(column++), course.semester(), styles.integer);
            set(row.createCell(column++), value(course.subjectCategory()), styles.text);
            set(row.createCell(column++), value(course.appliedSubjectCategory()), styles.text);
            set(row.createCell(column++), course.courseName(), styles.text);
            set(row.createCell(column++), value(course.gradeScale()), styles.text);
            set(row.createCell(column++), course.grade(), styles.integer);
            set(row.createCell(column++), value(course.achievement()), styles.text);
            set(row.createCell(column++), course.rankPosition(), styles.integer);
            set(row.createCell(column++), course.tiedRankCount(), styles.integer);
            set(row.createCell(column++), course.cohortSize(), styles.integer);
            set(row.createCell(column++), course.rankPercentile(), styles.decimal);
            set(row.createCell(column++), value(course.legacyAchievement()), styles.text);
            set(row.createCell(column++), course.effectiveGrade(), styles.decimal);
            set(row.createCell(column++), course.convertedScore(), styles.decimal);
            set(row.createCell(column++), course.gradeWeight(), styles.decimal);
            set(row.createCell(column++), course.subjectWeight(), styles.decimal);
            set(row.createCell(column++), course.credits(), styles.decimal);
            set(row.createCell(column++), course.appliedCredits(), styles.decimal);
            set(row.createCell(column++), course.appliedWeight(), styles.decimal);
            set(row.createCell(column), course.weightedScore(), styles.decimal);
        }

        sheet.createFreezePane(0, 3);
        if (!rows.isEmpty()) {
            sheet.setAutoFilter(new CellRangeAddress(2, rowIndex - 1, 0, COURSE_HEADERS.length - 1));
        }
        for (int column = 0; column < COURSE_HEADERS.length; column++) {
            int width = switch (column) {
                case 1 -> 30;
                case 4, 5, 6 -> 18;
                default -> 13;
            };
            sheet.setColumnWidth(column, width * 256);
        }
        sheet.setPrintGridlines(false);
    }

    private int section(Sheet sheet, Styles styles, int rowIndex, String title) {
        Row row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(styles.section);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 3));
        return rowIndex + 1;
    }

    private int keyValueRow(
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
        set(row.createCell(1), firstValue, styles.value);
        set(row.createCell(2), secondKey, styles.key);
        set(row.createCell(3), secondValue, styles.value);
        return rowIndex + 1;
    }

    private void set(Cell cell, Object value, CellStyle style) {
        cell.setCellStyle(style);
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value == null ? "" : value.toString());
        }
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private String rounding(int scale, Object roundingMode) {
        return "소수 " + scale + "자리 / " + value(roundingMode);
    }

    private String yearWeights(Map<Integer, BigDecimal> weights) {
        return weights.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "학년: " + entry.getValue())
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
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
        private final CellStyle included;
        private final CellStyle excluded;
        private final CellStyle warning;

        private Styles(XSSFWorkbook workbook) {
            title = base(workbook);
            title.setFillForegroundColor(color("174A37"));
            title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            title.setAlignment(HorizontalAlignment.LEFT);
            title.setVerticalAlignment(VerticalAlignment.CENTER);
            title.setFont(font(workbook, IndexedColors.WHITE.getIndex(), true, 16));

            section = base(workbook);
            section.setFillForegroundColor(color("DDECE2"));
            section.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            section.setFont(font(workbook, IndexedColors.DARK_GREEN.getIndex(), true, 11));

            key = bordered(workbook);
            key.setFillForegroundColor(color("EEF5F0"));
            key.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            key.setFont(font(workbook, IndexedColors.DARK_GREEN.getIndex(), true, 10));

            value = bordered(workbook);
            value.setWrapText(true);
            value.setDataFormat(workbook.createDataFormat().getFormat("0.######"));

            header = bordered(workbook);
            header.setFillForegroundColor(color("2E6849"));
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);
            header.setFont(font(workbook, IndexedColors.WHITE.getIndex(), true, 10));

            text = bordered(workbook);
            text.setWrapText(true);
            integer = bordered(workbook);
            integer.setDataFormat(workbook.createDataFormat().getFormat("0"));
            decimal = bordered(workbook);
            decimal.setDataFormat(workbook.createDataFormat().getFormat("0.######"));

            included = bordered(workbook);
            included.setFillForegroundColor(color("E5F3E8"));
            included.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            included.setFont(font(workbook, IndexedColors.DARK_GREEN.getIndex(), true, 10));
            excluded = bordered(workbook);
            excluded.setFillForegroundColor(color("FCE8E6"));
            excluded.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            excluded.setFont(font(workbook, IndexedColors.DARK_RED.getIndex(), true, 10));

            warning = base(workbook);
            warning.setFillForegroundColor(color("FFF4CC"));
            warning.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            warning.setWrapText(true);
            warning.setFont(font(workbook, IndexedColors.DARK_RED.getIndex(), false, 10));
        }

        private static XSSFCellStyle base(XSSFWorkbook workbook) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private static XSSFCellStyle bordered(XSSFWorkbook workbook) {
            XSSFCellStyle style = base(workbook);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            return style;
        }

        private static Font font(XSSFWorkbook workbook, short color, boolean bold, int size) {
            Font font = workbook.createFont();
            font.setColor(color);
            font.setBold(bold);
            font.setFontHeightInPoints((short) size);
            return font;
        }

        private static XSSFColor color(String hex) {
            return new XSSFColor(java.awt.Color.decode("#" + hex), null);
        }
    }
}
