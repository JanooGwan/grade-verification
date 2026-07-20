package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportRowError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class TranscriptExcelParser {

    private static final Logger log = LoggerFactory.getLogger(TranscriptExcelParser.class);
    private static final int MAX_DATA_ROWS = 10_000;
    private static final Set<String> REQUIRED_HEADERS = Set.of(
        "applicantNumber", "studentName", "schoolYear", "semester",
        "subjectCategory", "courseName", "credits"
    );
    private static final Map<String, String> HEADER_ALIASES = headerAliases();

    TranscriptExcelParseResult parse(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw CustomException.of(INVALID_TRANSCRIPT_FILE, "시트가 없습니다.");
            }
            return parseSheet(workbook.getSheetAt(0), workbook.getCreationHelper().createFormulaEvaluator());
        } catch (CustomException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            log.warn("Failed to parse transcript Excel file", exception);
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "읽을 수 있는 Excel 파일인지 확인해 주세요.");
        }
    }

    private TranscriptExcelParseResult parseSheet(Sheet sheet, FormulaEvaluator evaluator) {
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "헤더 행이 없습니다.");
        }

        Map<String, Integer> columns = readHeaders(headerRow, formatter, evaluator);
        List<String> missingHeaders = REQUIRED_HEADERS.stream()
            .filter(header -> !columns.containsKey(header))
            .sorted()
            .toList();
        if (!missingHeaders.isEmpty()) {
            throw CustomException.of(
                INVALID_TRANSCRIPT_FILE,
                "필수 헤더가 없습니다: "
                    + String.join(", ", missingHeaders.stream().map(this::displayName).toList())
            );
        }

        List<TranscriptExcelRow> rows = new ArrayList<>();
        List<TranscriptImportRowError> errors = new ArrayList<>();
        int totalRows = 0;
        for (int index = headerRow.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null || isBlank(row, formatter, evaluator)) {
                continue;
            }
            totalRows++;
            if (totalRows > MAX_DATA_ROWS) {
                throw CustomException.of(
                    INVALID_TRANSCRIPT_FILE,
                    "한 번에 최대 %,d행까지 업로드할 수 있습니다.".formatted(MAX_DATA_ROWS)
                );
            }
            try {
                rows.add(parseRow(row, columns, formatter, evaluator));
            } catch (IllegalArgumentException exception) {
                errors.add(new TranscriptImportRowError(index + 1, exception.getMessage()));
            }
        }
        return new TranscriptExcelParseResult(totalRows, List.copyOf(rows), List.copyOf(errors));
    }

    private Map<String, Integer> readHeaders(
        Row headerRow,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = normalize(formatter.formatCellValue(cell, evaluator));
            String canonical = HEADER_ALIASES.get(header);
            if (canonical != null) {
                columns.putIfAbsent(canonical, cell.getColumnIndex());
            }
        }
        return columns;
    }

    private TranscriptExcelRow parseRow(
        Row row,
        Map<String, Integer> columns,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        String applicantNumber = requiredText(row, columns, "applicantNumber", formatter, evaluator);
        String studentName = requiredText(row, columns, "studentName", formatter, evaluator);
        requireMaxLength(applicantNumber, 50, "지원자번호");
        requireMaxLength(studentName, 100, "학생명");
        int schoolYear = requiredInteger(row, columns, "schoolYear", formatter, evaluator, 1, 3);
        int semester = requiredInteger(row, columns, "semester", formatter, evaluator, 1, 2);
        String subjectText = requiredText(row, columns, "subjectCategory", formatter, evaluator);
        String courseName = requiredText(row, columns, "courseName", formatter, evaluator);
        requireMaxLength(courseName, 100, "과목명");
        BigDecimal credits = requiredDecimal(row, columns, "credits", formatter, evaluator);
        if (credits.signum() <= 0) {
            throw new IllegalArgumentException("이수단위는 0보다 커야 합니다.");
        }

        Integer grade = optionalInteger(row, columns, "grade", formatter, evaluator);
        if (grade != null && (grade < 1 || grade > 9)) {
            throw new IllegalArgumentException("석차등급은 1~9 사이여야 합니다.");
        }
        AchievementLevel achievement = parseAchievement(
            optionalText(row, columns, "achievement", formatter, evaluator)
        );
        if (grade == null && achievement == null) {
            throw new IllegalArgumentException("석차등급 또는 성취도 중 하나는 필요합니다.");
        }

        BigDecimal rawScore = optionalDecimal(row, columns, "rawScore", formatter, evaluator);
        BigDecimal meanScore = optionalDecimal(row, columns, "meanScore", formatter, evaluator);
        if ((rawScore != null && rawScore.signum() < 0) || (meanScore != null && meanScore.signum() < 0)) {
            throw new IllegalArgumentException("원점수와 과목평균은 0 이상이어야 합니다.");
        }
        BigDecimal standardDeviation = optionalDecimal(
            row, columns, "standardDeviation", formatter, evaluator
        );
        if (standardDeviation != null && standardDeviation.signum() <= 0) {
            throw new IllegalArgumentException("표준편차는 0보다 커야 합니다.");
        }
        Integer studentCount = optionalInteger(row, columns, "studentCount", formatter, evaluator);
        if (studentCount != null && studentCount < 1) {
            throw new IllegalArgumentException("수강자수는 1 이상이어야 합니다.");
        }

        String highSchoolCode = optionalText(row, columns, "highSchoolCode", formatter, evaluator);
        String highSchoolName = optionalText(row, columns, "highSchoolName", formatter, evaluator);
        requireMaxLength(highSchoolCode, 30, "고교코드");
        requireMaxLength(highSchoolName, 150, "고교명");
        Integer graduationYear = optionalInteger(row, columns, "graduationYear", formatter, evaluator);
        if (graduationYear != null && (graduationYear < 1900 || graduationYear > 2100)) {
            throw new IllegalArgumentException("졸업연도는 1900~2100 사이여야 합니다.");
        }

        return new TranscriptExcelRow(
            row.getRowNum() + 1,
            applicantNumber,
            studentName,
            highSchoolCode,
            highSchoolName,
            graduationYear,
            schoolYear,
            semester,
            parseSubjectCategory(subjectText, courseName),
            courseName,
            grade,
            achievement,
            rawScore,
            meanScore,
            standardDeviation,
            studentCount,
            credits,
            optionalBoolean(row, columns, "careerSubject", formatter, evaluator),
            optionalBoolean(row, columns, "professionalCourse", formatter, evaluator)
        );
    }

    private boolean isBlank(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell, evaluator).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String requiredText(
        Row row,
        Map<String, Integer> columns,
        String key,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        String value = optionalText(row, columns, key, formatter, evaluator);
        if (value == null) {
            throw new IllegalArgumentException(displayName(key) + " 값이 없습니다.");
        }
        return value;
    }

    private String optionalText(
        Row row,
        Map<String, Integer> columns,
        String key,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        Integer column = columns.get(key);
        if (column == null) {
            return null;
        }
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell, evaluator).trim();
        return value.isBlank() ? null : value;
    }

    private int requiredInteger(
        Row row,
        Map<String, Integer> columns,
        String key,
        DataFormatter formatter,
        FormulaEvaluator evaluator,
        int minimum,
        int maximum
    ) {
        Integer value = optionalInteger(row, columns, key, formatter, evaluator);
        if (value == null) {
            throw new IllegalArgumentException(displayName(key) + " 값이 없습니다.");
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                "%s은(는) %d~%d 사이여야 합니다.".formatted(displayName(key), minimum, maximum)
            );
        }
        return value;
    }

    private Integer optionalInteger(
        Row row,
        Map<String, Integer> columns,
        String key,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        String value = optionalText(row, columns, key, formatter, evaluator);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "")).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(displayName(key) + " 값이 정수가 아닙니다.");
        }
    }

    private BigDecimal requiredDecimal(
        Row row,
        Map<String, Integer> columns,
        String key,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        BigDecimal value = optionalDecimal(row, columns, key, formatter, evaluator);
        if (value == null) {
            throw new IllegalArgumentException(displayName(key) + " 값이 없습니다.");
        }
        return value;
    }

    private BigDecimal optionalDecimal(
        Row row,
        Map<String, Integer> columns,
        String key,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        String value = optionalText(row, columns, key, formatter, evaluator);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(displayName(key) + " 값이 숫자가 아닙니다.");
        }
    }

    private boolean optionalBoolean(
        Row row,
        Map<String, Integer> columns,
        String key,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        String value = optionalText(row, columns, key, formatter, evaluator);
        if (value == null) {
            return false;
        }
        return switch (normalize(value)) {
            case "y", "yes", "true", "1", "예", "해당" -> true;
            case "n", "no", "false", "0", "아니오", "비해당" -> false;
            default -> throw new IllegalArgumentException(displayName(key) + " 값은 Y/N 형식이어야 합니다.");
        };
    }

    private SubjectCategory parseSubjectCategory(String value, String courseName) {
        String normalized = normalize(value);
        if (normalized.contains("외국어") || normalized.contains("제2외국어")) {
            String normalizedCourseName = normalize(courseName);
            return normalizedCourseName.contains("영어") || normalizedCourseName.contains("english")
                ? SubjectCategory.ENGLISH
                : SubjectCategory.OTHER;
        }
        if (normalized.contains("국어") || normalized.equals("korean")) {
            return SubjectCategory.KOREAN;
        }
        if (normalized.contains("수학") || normalized.equals("math")) {
            return SubjectCategory.MATH;
        }
        if (normalized.contains("영어") || normalized.equals("english")) {
            return SubjectCategory.ENGLISH;
        }
        if (normalized.contains("사회") || normalized.contains("역사")
            || normalized.contains("도덕") || normalized.equals("social")) {
            return SubjectCategory.SOCIAL;
        }
        if (normalized.contains("과학") || normalized.equals("science")) {
            return SubjectCategory.SCIENCE;
        }
        if (normalized.equals("기타") || normalized.equals("other")) {
            return SubjectCategory.OTHER;
        }
        throw new IllegalArgumentException("지원하지 않는 교과입니다: " + value);
    }

    private AchievementLevel parseAchievement(String value) {
        if (value == null) {
            return null;
        }
        try {
            return AchievementLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("성취도는 A~E 중 하나여야 합니다.");
        }
    }

    private String displayName(String key) {
        return switch (key) {
            case "applicantNumber" -> "지원자번호";
            case "studentName" -> "학생명";
            case "schoolYear" -> "학년";
            case "semester" -> "학기";
            case "subjectCategory" -> "교과";
            case "courseName" -> "과목명";
            case "grade" -> "석차등급";
            case "achievement" -> "성취도";
            case "rawScore" -> "원점수";
            case "meanScore" -> "과목평균";
            case "standardDeviation" -> "표준편차";
            case "studentCount" -> "수강자수";
            case "credits" -> "이수단위";
            case "careerSubject" -> "진로선택";
            case "professionalCourse" -> "전문교과";
            default -> key;
        };
    }

    private void requireMaxLength(String value, int maximum, String fieldName) {
        if (value != null && value.length() > maximum) {
            throw new IllegalArgumentException(
                "%s은(는) 최대 %d자까지 입력할 수 있습니다.".formatted(fieldName, maximum)
            );
        }
    }

    private String normalize(String value) {
        return value.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[\\s_\\-()]", "");
    }

    private static Map<String, String> headerAliases() {
        Map<String, String> aliases = new HashMap<>();
        addAliases(aliases, "applicantNumber", "지원자번호", "수험번호", "applicantnumber");
        addAliases(aliases, "studentName", "학생명", "성명", "studentname");
        addAliases(aliases, "highSchoolCode", "고교코드", "출신고교코드", "highschoolcode");
        addAliases(aliases, "highSchoolName", "고교명", "출신고교명", "highschoolname");
        addAliases(aliases, "graduationYear", "졸업연도", "graduationyear");
        addAliases(aliases, "schoolYear", "학년", "schoolyear");
        addAliases(aliases, "semester", "학기", "semester");
        addAliases(aliases, "subjectCategory", "교과", "교과구분", "subjectcategory");
        addAliases(aliases, "courseName", "과목명", "교과목명", "coursename");
        addAliases(aliases, "grade", "석차등급", "등급", "grade");
        addAliases(aliases, "achievement", "성취도", "achievement");
        addAliases(aliases, "rawScore", "원점수", "rawscore");
        addAliases(aliases, "meanScore", "과목평균", "평균", "meanscore");
        addAliases(aliases, "standardDeviation", "표준편차", "standarddeviation");
        addAliases(aliases, "studentCount", "수강자수", "재적수", "studentcount");
        addAliases(aliases, "credits", "이수단위", "단위수", "credits");
        addAliases(aliases, "careerSubject", "진로선택", "진로선택과목", "careersubject");
        addAliases(aliases, "professionalCourse", "전문교과", "professionalcourse");
        return Map.copyOf(aliases);
    }

    private static void addAliases(Map<String, String> aliases, String canonical, String... names) {
        for (String name : names) {
            aliases.put(name, canonical);
        }
    }
}
