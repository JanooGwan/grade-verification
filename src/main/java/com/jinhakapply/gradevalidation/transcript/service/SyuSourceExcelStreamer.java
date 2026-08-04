package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

@Component
class SyuSourceExcelStreamer {
    static final String SOURCE_FORMAT = "SYU_SOURCE_WORKBOOK_V1";
    private static final String COURSE_SHEET = "학생부 교과 성적";
    private static final String ATTENDANCE_SHEET = "학생부출결";
    private static final int MAX_ROWS = 1_500_000;
    private static final int MAX_ERRORS = 1_000;

    SourceScanResult scan(Path path) {
        Set<String> applicants = new HashSet<>();
        Set<Integer> admissionYears = new HashSet<>();
        int[] rows = {0};
        readSheet(path, COURSE_SHEET, (rowNumber, values) -> {
            if (rowNumber == 0) {
                requireHeaders(values, Map.of(
                    0, "입학연도", 2, "수험번호", 3, "학년", 4, "학기",
                    10, "과목명", 11, "이수단위"
                ));
                return;
            }
            if (++rows[0] > MAX_ROWS) throw tooManyRows();
            Integer year = integer(values.get(0));
            String applicantNumber = clean(values.get(2));
            if (year != null) admissionYears.add(year);
            if (applicantNumber != null) applicants.add(applicantNumber);
        });
        readSheet(path, ATTENDANCE_SHEET, (rowNumber, values) -> {
            if (rowNumber == 0) return;
            Integer year = integer(values.get(0));
            String applicantNumber = clean(values.get(2));
            if (year != null) admissionYears.add(year);
            if (applicantNumber != null) applicants.add(applicantNumber);
        });
        if (rows[0] == 0 || applicants.isEmpty()) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "학생부 교과 성적 시트에 데이터가 없습니다.");
        }
        return new SourceScanResult(Set.copyOf(applicants), Set.copyOf(admissionYears), rows[0]);
    }

    StreamResult streamCourses(Path path, int batchSize, Consumer<List<SourceCourseRow>> consumer) {
        List<SourceCourseRow> batch = new ArrayList<>(batchSize);
        List<String> errors = new ArrayList<>();
        int[] imported = {0};
        int[] failed = {0};
        readSheet(path, COURSE_SHEET, (rowNumber, values) -> {
            if (rowNumber == 0) return;
            try {
                SourceCourseRow row = parseCourse(rowNumber + 1, values);
                if (row == null) return;
                batch.add(row);
                imported[0]++;
                if (batch.size() >= batchSize) {
                    consumer.accept(List.copyOf(batch));
                    batch.clear();
                }
            } catch (IllegalArgumentException exception) {
                failed[0]++;
                if (errors.size() < MAX_ERRORS) errors.add("%d행: %s".formatted(rowNumber + 1, exception.getMessage()));
            }
        });
        if (!batch.isEmpty()) consumer.accept(List.copyOf(batch));
        return new StreamResult(imported[0], failed[0], List.copyOf(errors));
    }

    StreamResult streamAttendance(Path path, int batchSize, Consumer<List<SourceAttendanceRow>> consumer) {
        List<SourceAttendanceRow> batch = new ArrayList<>(batchSize);
        List<String> errors = new ArrayList<>();
        int[] imported = {0};
        int[] failed = {0};
        readSheet(path, ATTENDANCE_SHEET, (rowNumber, values) -> {
            if (rowNumber == 0) {
                requireHeaders(values, Map.of(0, "입학연도", 2, "수험번호", 3, "학년"));
                return;
            }
            try {
                SourceAttendanceRow row = parseAttendance(rowNumber + 1, values);
                batch.add(row);
                imported[0]++;
                if (batch.size() >= batchSize) {
                    consumer.accept(List.copyOf(batch));
                    batch.clear();
                }
            } catch (IllegalArgumentException exception) {
                failed[0]++;
                if (errors.size() < MAX_ERRORS) errors.add("출결 %d행: %s".formatted(rowNumber + 1, exception.getMessage()));
            }
        });
        if (!batch.isEmpty()) consumer.accept(List.copyOf(batch));
        return new StreamResult(imported[0], failed[0], List.copyOf(errors));
    }

    private SourceCourseRow parseCourse(int rowNumber, Map<Integer, String> values) {
        String applicant = required(values, 2, "수험번호");
        int schoolYear = requiredInteger(values, 3, "학년", 1, 3);
        int semester = requiredInteger(values, 4, "학기", 1, 2);
        String organization = firstNonBlank(clean(values.get(8)), clean(values.get(6)));
        String courseName = required(values, 10, "과목명");
        BigDecimal credits = decimal(values.get(11));
        if (credits == null || credits.signum() <= 0) throw new IllegalArgumentException("이수단위가 0보다 커야 합니다.");
        Integer rank = positiveInteger(values.get(12));
        Integer studentCount = positiveInteger(values.get(13));
        Integer tiedRank = positiveInteger(values.get(14));
        Integer grade = positiveInteger(values.get(18));
        if (grade != null && grade > 9) throw new IllegalArgumentException("석차등급은 1~9여야 합니다.");
        AchievementLevel achievement = achievement(values.get(19));
        return new SourceCourseRow(
            rowNumber, applicant, schoolYear, semester, subjectCategory(organization), courseName,
            credits, rank, studentCount, tiedRank, decimal(values.get(15)), decimal(values.get(16)),
            positiveDecimal(values.get(17)), grade, achievement,
            grade == null && achievement != null, isProfessional(organization)
        );
    }

    private SourceAttendanceRow parseAttendance(int rowNumber, Map<Integer, String> values) {
        return new SourceAttendanceRow(
            rowNumber,
            required(values, 2, "수험번호"),
            requiredInteger(values, 3, "학년", 1, 3),
            nonNegativeInteger(values.get(6)),
            nonNegativeInteger(values.get(9)),
            nonNegativeInteger(values.get(12)),
            nonNegativeInteger(values.get(15))
        );
    }

    private void readSheet(Path path, String targetSheet, RowConsumer consumer) {
        boolean found = false;
        try (OPCPackage pkg = OPCPackage.open(path.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            SharedStrings strings = reader.getSharedStringsTable();
            XSSFReader.SheetIterator iterator = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (iterator.hasNext()) {
                try (InputStream sheet = iterator.next()) {
                    if (!targetSheet.equals(iterator.getSheetName())) continue;
                    found = true;
                    XMLReader xmlReader = XMLHelper.newXMLReader();
                    xmlReader.setContentHandler(new XSSFSheetXMLHandler(
                        styles, null, strings, new RowHandler(consumer), new DataFormatter(Locale.KOREA), false
                    ));
                    xmlReader.parse(new InputSource(sheet));
                    break;
                }
            }
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                targetSheet + " 시트를 읽지 못했습니다: " + exception.getMessage());
        }
        if (!found) throw CustomException.of(INVALID_TRANSCRIPT_FILE, targetSheet + " 시트가 없습니다.");
    }

    private void requireHeaders(Map<Integer, String> values, Map<Integer, String> expected) {
        expected.forEach((index, name) -> {
            if (!name.equals(clean(values.get(index)))) {
                throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                    "%s 열의 헤더가 올바르지 않습니다. 예상: %s".formatted(columnName(index), name));
            }
        });
    }

    private String columnName(int index) {
        return Character.toString('A' + index);
    }

    private CustomException tooManyRows() {
        return CustomException.of(INVALID_TRANSCRIPT_FILE, "교과 성적은 최대 %,d행까지 처리할 수 있습니다.".formatted(MAX_ROWS));
    }

    private String required(Map<Integer, String> values, int index, String label) {
        String value = clean(values.get(index));
        if (value == null) throw new IllegalArgumentException(label + " 값이 없습니다.");
        return value;
    }

    private int requiredInteger(Map<Integer, String> values, int index, String label, int min, int max) {
        Integer value = integer(values.get(index));
        if (value == null || value < min || value > max) {
            throw new IllegalArgumentException(label + "은(는) " + min + "~" + max + "여야 합니다.");
        }
        return value;
    }

    private Integer integer(String value) {
        String cleaned = clean(value);
        if (cleaned == null) return null;
        try {
            return new BigDecimal(cleaned.replace(",", "")).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    private int nonNegativeInteger(String value) {
        Integer result = integer(value);
        return result == null || result < 0 ? 0 : result;
    }

    private Integer positiveInteger(String value) {
        Integer result = integer(value);
        return result == null || result <= 0 ? null : result;
    }

    private BigDecimal decimal(String value) {
        String cleaned = clean(value);
        if (cleaned == null) return null;
        try {
            return new BigDecimal(cleaned.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal positiveDecimal(String value) {
        BigDecimal result = decimal(value);
        return result == null || result.signum() <= 0 ? null : result;
    }

    private AchievementLevel achievement(String value) {
        String cleaned = clean(value);
        if (cleaned == null) return null;
        try {
            return AchievementLevel.valueOf(cleaned.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private SubjectCategory subjectCategory(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s", "");
        if (normalized.contains("국어")) return SubjectCategory.KOREAN;
        if (normalized.contains("수학")) return SubjectCategory.MATH;
        if (normalized.contains("영어")) return SubjectCategory.ENGLISH;
        if (normalized.contains("과학")) return SubjectCategory.SCIENCE;
        if (normalized.contains("사회") || normalized.contains("역사") || normalized.contains("도덕")) {
            return SubjectCategory.SOCIAL;
        }
        return SubjectCategory.OTHER;
    }

    private boolean isProfessional(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s", "");
        return normalized.contains("전문교과") || normalized.contains("전문") || normalized.contains("특성화");
    }

    private String firstNonBlank(String first, String second) {
        return first == null ? second == null ? "" : second : first;
    }

    private String clean(String value) {
        return value == null || value.isBlank() || "NULL".equalsIgnoreCase(value.trim()) ? null : value.trim();
    }

    record SourceScanResult(Set<String> applicantNumbers, Set<Integer> admissionYears, int courseRows) {}
    record StreamResult(int importedRows, int failedRows, List<String> errors) {}
    record SourceCourseRow(
        int rowNumber, String applicantNumber, int schoolYear, int semester, SubjectCategory subjectCategory,
        String courseName, BigDecimal credits, Integer rankPosition, Integer studentCount, Integer tiedRankCount,
        BigDecimal rawScore, BigDecimal meanScore, BigDecimal standardDeviation, Integer grade,
        AchievementLevel achievement, boolean careerSubject, boolean professionalCourse
    ) {}
    record SourceAttendanceRow(
        int rowNumber, String applicantNumber, int schoolYear, int absenceDays,
        int tardyCount, int earlyLeaveCount, int classAbsenceCount
    ) {}

    @FunctionalInterface
    private interface RowConsumer {
        void accept(int rowNumber, Map<Integer, String> values);
    }

    private static final class RowHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final RowConsumer consumer;
        private final Map<Integer, String> values = new HashMap<>();
        private int rowNumber;

        private RowHandler(RowConsumer consumer) {
            this.consumer = consumer;
        }

        @Override public void startRow(int rowNum) { rowNumber = rowNum; values.clear(); }
        @Override public void endRow(int rowNum) {
            if (!values.isEmpty()) consumer.accept(rowNumber, Map.copyOf(values));
        }
        @Override public void cell(String reference, String formattedValue, XSSFComment comment) {
            values.put(columnIndex(reference), formattedValue);
        }
        private int columnIndex(String reference) {
            int value = 0;
            for (int index = 0; index < reference.length() && Character.isLetter(reference.charAt(index)); index++) {
                value = value * 26 + Character.toUpperCase(reference.charAt(index)) - 'A' + 1;
            }
            return value - 1;
        }
    }
}
