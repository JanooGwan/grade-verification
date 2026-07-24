package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class ApplicantSchoolInfoExcelParser {
    private static final int MAX_ROWS = 100_000;
    private static final List<String> REQUIRED_HEADERS = List.of(
        "입학연도", "수험번호", "졸업연도", "고교코드", "고교명",
        "학과코드", "고교타입", "고교구분", "지원자_고교구분코드"
    );

    ApplicantSchoolInfoParseResult parse(MultipartFile file) {
        if (file == null || file.isEmpty()) return ApplicantSchoolInfoParseResult.empty();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw invalid("지원자 추가정보 Excel에 시트가 없습니다.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) throw invalid("지원자 추가정보 Excel에 헤더가 없습니다.");
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            Map<String, Integer> columns = columns(headerRow, formatter);
            List<String> missing = REQUIRED_HEADERS.stream().filter(header -> !columns.containsKey(header)).toList();
            if (!missing.isEmpty()) {
                throw invalid("지원자 추가정보 필수 열이 없습니다: " + String.join(", ", missing));
            }

            Map<String, ApplicantSchoolInfoRow> rows = new LinkedHashMap<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                if (rows.size() >= MAX_ROWS) {
                    throw invalid("지원자 추가정보는 최대 %,d행까지 처리할 수 있습니다.".formatted(MAX_ROWS));
                }
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                String applicantNumber = text(row, columns.get("수험번호"), formatter);
                if (applicantNumber == null) continue;
                ApplicantSchoolInfoRow parsed = parseRow(row, rowIndex + 1, columns, formatter, applicantNumber);
                if (rows.putIfAbsent(applicantNumber, parsed) != null) {
                    throw invalid("지원자 추가정보에 수험번호가 중복되었습니다: " + applicantNumber);
                }
            }
            return new ApplicantSchoolInfoParseResult(List.copyOf(rows.values()), Map.copyOf(rows));
        } catch (CustomException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalid("지원자 추가정보 Excel을 읽지 못했습니다.");
        }
    }

    private ApplicantSchoolInfoRow parseRow(
        Row row,
        int rowNumber,
        Map<String, Integer> columns,
        DataFormatter formatter,
        String applicantNumber
    ) {
        Integer admissionYear = integer(row, columns.get("입학연도"), formatter);
        Integer graduationYear = integer(row, columns.get("졸업연도"), formatter);
        String highSchoolCode = text(row, columns.get("고교코드"), formatter);
        String highSchoolName = text(row, columns.get("고교명"), formatter);
        String departmentCode = text(row, columns.get("학과코드"), formatter);
        String sourceType = text(row, columns.get("고교타입"), formatter);
        String sourceCategory = text(row, columns.get("고교구분"), formatter);
        String applicantCategoryCode = text(row, columns.get("지원자_고교구분코드"), formatter);
        EducationBackground educationBackground = educationBackground(
            highSchoolName, sourceType, sourceCategory
        );
        HighSchoolType highSchoolType = highSchoolType(
            highSchoolName, sourceType, sourceCategory, applicantCategoryCode, educationBackground
        );
        return new ApplicantSchoolInfoRow(
            rowNumber, admissionYear, applicantNumber, graduationYear, highSchoolCode, highSchoolName,
            departmentCode, sourceType, sourceCategory, applicantCategoryCode,
            educationBackground, highSchoolType
        );
    }

    private EducationBackground educationBackground(String name, String type, String category) {
        String combined = normalize(name) + normalize(type) + normalize(category);
        if (combined.contains("검정고시")) return EducationBackground.GED;
        if (combined.contains("외국고") || combined.contains("외국소재고")) {
            return EducationBackground.FOREIGN_HIGH_SCHOOL;
        }
        return EducationBackground.DOMESTIC_HIGH_SCHOOL;
    }

    private HighSchoolType highSchoolType(
        String name,
        String type,
        String category,
        String applicantCategoryCode,
        EducationBackground educationBackground
    ) {
        if (educationBackground != EducationBackground.DOMESTIC_HIGH_SCHOOL) return HighSchoolType.GENERAL;
        String normalizedName = normalize(name);
        String normalizedType = normalize(type);
        String normalizedCategory = normalize(category);
        String normalizedApplicantCategory = normalize(applicantCategoryCode);
        if (normalizedName.contains("학력인정") || normalizedType.contains("학력인정")
            || normalizedCategory.contains("학력인정")) {
            return HighSchoolType.LIFELONG_EDUCATION_FACILITY;
        }
        boolean professional = normalizedApplicantCategory.contains("전문계고교")
            || normalizedCategory.contains("특성화고")
            || normalizedCategory.contains("실업계")
            || normalizedType.contains("특성화고")
            || normalizedType.equals("특성")
            || normalizedType.contains("실업고");
        if (!professional) return HighSchoolType.GENERAL;
        if (normalizedType.contains("종합") || normalizedCategory.contains("종합고")) {
            return HighSchoolType.COMPREHENSIVE_VOCATIONAL;
        }
        return HighSchoolType.SPECIALIZED;
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
            return new java.math.BigDecimal(value.replace(",", "")).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    private String text(Row row, int column, DataFormatter formatter) {
        String value = formatter.formatCellValue(row.getCell(column));
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s·_/()\\-]", "").toLowerCase(Locale.ROOT);
    }

    private CustomException invalid(String detail) {
        return CustomException.of(INVALID_TRANSCRIPT_FILE, detail);
    }
}
