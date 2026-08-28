package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.jinhakapply.gradevalidation.global.exception.CustomException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class VocationalTrainingExcelParser {
    private static final int MAX_ROWS = 100_000;
    private static final String ADMISSION_YEAR_HEADER = "입학연도";
    private static final String APPLICANT_NUMBER_HEADER = "수험번호";
    private static final List<SemesterColumn> SEMESTER_COLUMNS = List.of(
        new SemesterColumn("1학년 1학기", new VocationalTrainingSemester(1, 1)),
        new SemesterColumn("1학년 2학기", new VocationalTrainingSemester(1, 2)),
        new SemesterColumn("2학년 1학기", new VocationalTrainingSemester(2, 1)),
        new SemesterColumn("2학년 2학기", new VocationalTrainingSemester(2, 2)),
        new SemesterColumn("3학년 1학기", new VocationalTrainingSemester(3, 1)),
        new SemesterColumn("3학년 2학기", new VocationalTrainingSemester(3, 2))
    );

    VocationalTrainingParseResult parse(MultipartFile file, int expectedAdmissionYear) {
        if (file == null || file.isEmpty()) return VocationalTrainingParseResult.empty();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) throw invalid("직업과정 위탁생 Excel에 시트가 없습니다.");
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) throw invalid("직업과정 위탁생 Excel에 헤더가 없습니다.");
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            Map<String, Integer> columns = columns(headerRow, formatter);
            List<String> requiredHeaders = new java.util.ArrayList<>();
            requiredHeaders.add(ADMISSION_YEAR_HEADER);
            requiredHeaders.add(APPLICANT_NUMBER_HEADER);
            SEMESTER_COLUMNS.forEach(column -> requiredHeaders.add(column.header()));
            List<String> missing = requiredHeaders.stream().filter(header -> !columns.containsKey(header)).toList();
            if (!missing.isEmpty()) {
                throw invalid("직업과정 위탁생 필수 열이 없습니다: " + String.join(", ", missing));
            }

            Map<String, Set<VocationalTrainingSemester>> result = new LinkedHashMap<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                if (result.size() >= MAX_ROWS) {
                    throw invalid("직업과정 위탁생은 최대 %,d행까지 처리할 수 있습니다.".formatted(MAX_ROWS));
                }
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                String applicantNumber = text(row, columns.get(APPLICANT_NUMBER_HEADER), formatter);
                if (applicantNumber == null) continue;
                Integer admissionYear = integer(row, columns.get(ADMISSION_YEAR_HEADER), formatter);
                if (admissionYear != null && admissionYear != expectedAdmissionYear) {
                    throw invalid("화면의 모집연도와 직업과정 위탁생 파일의 입학연도가 일치하지 않습니다.");
                }
                Set<VocationalTrainingSemester> semesters = new LinkedHashSet<>();
                for (SemesterColumn column : SEMESTER_COLUMNS) {
                    if (text(row, columns.get(column.header()), formatter) != null) {
                        semesters.add(column.semester());
                    }
                }
                if (result.putIfAbsent(applicantNumber, Set.copyOf(semesters)) != null) {
                    throw invalid("직업과정 위탁생 파일에 수험번호가 중복되었습니다: " + applicantNumber);
                }
            }
            return new VocationalTrainingParseResult(Map.copyOf(result));
        } catch (CustomException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalid("직업과정 위탁생 Excel을 읽지 못했습니다.");
        }
    }

    private Map<String, Integer> columns(Row row, DataFormatter formatter) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int column = row.getFirstCellNum(); column < row.getLastCellNum(); column++) {
            String value = text(row, column, formatter);
            if (value != null) columns.put(value, column);
        }
        return columns;
    }

    private Integer integer(Row row, int column, DataFormatter formatter) {
        String value = text(row, column, formatter);
        if (value == null) return null;
        try {
            return new BigDecimal(value.replace(",", "")).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    private String text(Row row, int column, DataFormatter formatter) {
        String value = formatter.formatCellValue(row.getCell(column));
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private CustomException invalid(String detail) {
        return CustomException.of(INVALID_TRANSCRIPT_FILE, detail);
    }

    private record SemesterColumn(String header, VocationalTrainingSemester semester) {
    }
}
