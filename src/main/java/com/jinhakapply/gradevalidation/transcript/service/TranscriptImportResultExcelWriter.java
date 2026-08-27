package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
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
class TranscriptImportResultExcelWriter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    byte[] write(StudentTranscriptImport transcriptImport) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("가져오기 결과");
            sheet.setDisplayGridlines(false);
            sheet.createFreezePane(0, 2);

            CellStyle titleStyle = titleStyle(workbook);
            CellStyle sectionStyle = sectionStyle(workbook);
            CellStyle keyStyle = keyStyle(workbook);
            CellStyle valueStyle = valueStyle(workbook);
            CellStyle countStyle = valueStyle(workbook);
            countStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            CellStyle noteStyle = noteStyle(workbook);

            Row title = sheet.createRow(0);
            title.setHeightInPoints(32);
            set(title.createCell(0), "학생부 가져오기 처리 결과", titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            Row note = sheet.createRow(1);
            note.setHeightInPoints(36);
            set(note.createCell(0), "이 문서는 원천 데이터의 DB 적재 결과입니다. 대학별 환산점수 검증 결과가 아닙니다.", noteStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 3));

            int rowIndex = 3;
            rowIndex = section(sheet, rowIndex, "작업 정보", sectionStyle);
            rowIndex = keyValue(sheet, rowIndex, "작업 번호", transcriptImport.getId(), "상태", transcriptImport.getStatus().name(), keyStyle, valueStyle);
            rowIndex = keyValue(sheet, rowIndex, "모집연도", transcriptImport.getAdmissionYear(), "원천 형식", transcriptImport.getSourceFormat(), keyStyle, valueStyle);
            rowIndex = keyValue(sheet, rowIndex, "원본 파일명", transcriptImport.getOriginalFileName(), "처리 시작", transcriptImport.getCreatedAt().format(DATE_TIME), keyStyle, valueStyle);
            rowIndex = keyValue(sheet, rowIndex, "파일 SHA-256", transcriptImport.getFileSha256(), "최종 갱신", transcriptImport.getUpdatedAt().format(DATE_TIME), keyStyle, valueStyle);

            rowIndex++;
            rowIndex = section(sheet, rowIndex, "처리 집계", sectionStyle);
            rowIndex = keyValue(sheet, rowIndex, "전체 행", transcriptImport.getTotalRows(), "적재 행", transcriptImport.getImportedRows(), keyStyle, countStyle);
            rowIndex = keyValue(sheet, rowIndex, "오류 행", transcriptImport.getFailedRows(), "성공률", successRate(transcriptImport), keyStyle, valueStyle);

            rowIndex++;
            rowIndex = section(sheet, rowIndex, "처리 메시지", sectionStyle);
            Row message = sheet.createRow(rowIndex);
            message.setHeightInPoints(42);
            set(message.createCell(0), transcriptImport.getErrorMessage() == null ? "오류 없이 완료되었습니다." : transcriptImport.getErrorMessage(), noteStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 3));

            sheet.setColumnWidth(0, 20 * 256);
            sheet.setColumnWidth(1, 48 * 256);
            sheet.setColumnWidth(2, 20 * 256);
            sheet.setColumnWidth(3, 34 * 256);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "가져오기 처리 결과 Excel 파일을 생성하지 못했습니다.");
        }
    }

    private int section(Sheet sheet, int rowIndex, String label, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        set(row.createCell(0), label, style);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 3));
        return rowIndex + 1;
    }

    private int keyValue(
        Sheet sheet,
        int rowIndex,
        String firstKey,
        Object firstValue,
        String secondKey,
        Object secondValue,
        CellStyle keyStyle,
        CellStyle valueStyle
    ) {
        Row row = sheet.createRow(rowIndex);
        set(row.createCell(0), firstKey, keyStyle);
        set(row.createCell(1), firstValue, valueStyle);
        set(row.createCell(2), secondKey, keyStyle);
        set(row.createCell(3), secondValue, valueStyle);
        return rowIndex + 1;
    }

    private String successRate(StudentTranscriptImport transcriptImport) {
        if (transcriptImport.getTotalRows() == 0) return "0.00%";
        return String.format("%.2f%%", transcriptImport.getImportedRows() * 100.0 / transcriptImport.getTotalRows());
    }

    private void set(Cell cell, Object value, CellStyle style) {
        cell.setCellStyle(style);
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(value == null ? "" : value.toString());
    }

    private CellStyle titleStyle(XSSFWorkbook workbook) {
        CellStyle style = baseStyle(workbook, "174A37", IndexedColors.WHITE.getIndex(), true, 16);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    private CellStyle sectionStyle(XSSFWorkbook workbook) {
        return baseStyle(workbook, "DDECE2", IndexedColors.DARK_GREEN.getIndex(), true, 11);
    }

    private CellStyle keyStyle(XSSFWorkbook workbook) {
        return baseStyle(workbook, "EEF5F0", IndexedColors.DARK_GREEN.getIndex(), true, 10);
    }

    private CellStyle valueStyle(XSSFWorkbook workbook) {
        return baseStyle(workbook, null, IndexedColors.BLACK.getIndex(), false, 10);
    }

    private CellStyle noteStyle(XSSFWorkbook workbook) {
        CellStyle style = baseStyle(workbook, "FFF4CC", IndexedColors.DARK_RED.getIndex(), false, 10);
        style.setWrapText(true);
        return style;
    }

    private CellStyle baseStyle(
        XSSFWorkbook workbook,
        String fill,
        short fontColor,
        boolean bold,
        int fontSize
    ) {
        XSSFCellStyle style = workbook.createCellStyle();
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
}
