package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.xml.parsers.ParserConfigurationException;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportRowError;
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
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

@Component
class TransferExcelParser {

    private static final Logger log = LoggerFactory.getLogger(TransferExcelParser.class);

    private static final String APPLICATION_SHEET = "vwapplyinfo";
    private static final String COURSE_SHEET = "hsbsubjectscore";
    private static final String FORMATION_SHEET = "codeformation";
    private static final int MAX_COURSE_ROWS = 500_000;
    private static final int MAX_REPORTED_ERRORS = 1_000;

    boolean supports(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) {
            return false;
        }
        Path temporaryFile = null;
        try {
            temporaryFile = copyToTemporaryFile(file, "transfer-detect-");
            try (OPCPackage pkg = OPCPackage.open(temporaryFile.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            Set<String> sheets = new HashSet<>();
            try (InputStream workbookData = reader.getWorkbookData()) {
                XMLReader xmlReader = XMLHelper.newXMLReader();
                xmlReader.setContentHandler(new DefaultHandler() {
                    @Override
                    public void startElement(String uri, String localName, String qName, Attributes attributes) {
                        if ("sheet".equals(localName) || "sheet".equals(qName)) {
                            String sheetName = attributes.getValue("name");
                            if (sheetName != null) sheets.add(sheetName.toLowerCase(Locale.ROOT));
                        }
                    }
                });
                xmlReader.parse(new InputSource(workbookData));
            }
            return sheets.contains(APPLICATION_SHEET) && sheets.contains(COURSE_SHEET);
            }
        } catch (Exception exception) {
            log.debug("Transfer workbook detection failed", exception);
            return false;
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    TransferExcelParseResult parse(MultipartFile file) {
        List<TransferApplicationRow> applications = new ArrayList<>();
        List<TranscriptExcelRow> courses = new ArrayList<>();
        List<TranscriptImportRowError> errors = new ArrayList<>();
        List<TranscriptImportRowError> skipped = new ArrayList<>();
        Set<String> found = new HashSet<>();
        Map<Integer, CourseSourceMetadata> courseMetadata = new HashMap<>();
        Map<String, EnumSet<CourseNature>> formationsByCourseCode = new HashMap<>();
        Map<String, EnumSet<CourseNature>> formationsByCourseName = new HashMap<>();
        int[] invalidRows = {0};
        int[] skippedRows = {0};
        int[] missingAssessmentRows = {0};
        Path temporaryFile = null;
        try {
            temporaryFile = copyToTemporaryFile(file, "transfer-import-");
            try (OPCPackage pkg = OPCPackage.open(temporaryFile.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            SharedStrings strings = reader.getSharedStringsTable();
            XSSFReader.SheetIterator iterator = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (iterator.hasNext()) {
                try (InputStream sheet = iterator.next()) {
                    String sheetName = iterator.getSheetName().toLowerCase(Locale.ROOT);
                    if (APPLICATION_SHEET.equals(sheetName)) {
                        found.add(APPLICATION_SHEET);
                        readSheet(styles, strings, sheet, (rowNumber, values) -> {
                            if (rowNumber == 0) return;
                            try {
                                applications.add(parseApplication(rowNumber + 1, values));
                            } catch (IllegalArgumentException exception) {
                                addError(errors, invalidRows, rowNumber + 1, exception.getMessage());
                            }
                        });
                    } else if (COURSE_SHEET.equals(sheetName)) {
                        found.add(COURSE_SHEET);
                        readSheet(styles, strings, sheet, (rowNumber, values) -> {
                            if (rowNumber == 0) return;
                            if (courses.size() + invalidRows[0] + skippedRows[0] >= MAX_COURSE_ROWS) {
                                throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                                    "전달양식 성적은 한 번에 최대 %,d행까지 처리할 수 있습니다.".formatted(MAX_COURSE_ROWS));
                            }
                            try {
                                TranscriptExcelRow course = parseCourse(rowNumber + 1, values);
                                courseMetadata.put(course.rowNumber(), new CourseSourceMetadata(
                                    optional(values, 6), optional(values, 7), course.courseName()
                                ));
                                if (course.grade() == null && course.achievement() == null
                                    && course.rankPosition() == null) {
                                    missingAssessmentRows[0]++;
                                }
                                courses.add(course);
                            } catch (IllegalArgumentException exception) {
                                addError(errors, invalidRows, rowNumber + 1, exception.getMessage());
                            }
                        });
                    } else if (FORMATION_SHEET.equals(sheetName)) {
                        readSheet(styles, strings, sheet, (rowNumber, values) -> {
                            if (rowNumber == 0) return;
                            String formationName = optional(values, 3);
                            if (formationName == null) return;
                            CourseNature nature = isOrdinaryOrganization(formationName)
                                ? CourseNature.ORDINARY : CourseNature.NON_ORDINARY;
                            addCourseNature(formationsByCourseCode, optional(values, 6), nature);
                            addCourseNature(formationsByCourseName, optional(values, 7), nature);
                        });
                    }
                }
            }
            }
            if (!found.containsAll(Set.of(APPLICATION_SHEET, COURSE_SHEET))) {
                throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                    "한신대 전달양식 시트(vwapplyinfo, hsbsubjectscore)를 찾을 수 없습니다.");
            }
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Failed to parse transfer workbook", exception);
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "전달양식 Excel 파일을 읽지 못했습니다.");
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
        for (int index = 0; index < courses.size(); index++) {
            TranscriptExcelRow course = courses.get(index);
            CourseSourceMetadata metadata = courseMetadata.get(course.rowNumber());
            boolean professionalCourse = isNonOrdinaryCourse(
                metadata, formationsByCourseCode, formationsByCourseName
            );
            courses.set(index, withProfessionalCourse(course, professionalCourse));
        }
        if (courses.isEmpty()) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "가져올 과목 성적이 없습니다.");
        }
        return new TransferExcelParseResult(
            "HANSHIN_MULTI_SHEET_V1",
            List.copyOf(applications),
            List.copyOf(courses),
            invalidRows[0],
            skippedRows[0],
            List.copyOf(skipped),
            List.copyOf(errors),
            warnings(missingAssessmentRows[0])
        );
    }

    private void deleteTemporaryFile(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (java.io.IOException exception) {
            log.warn("Failed to delete temporary transfer workbook: {}", path.getFileName());
        }
    }

    private Path copyToTemporaryFile(MultipartFile file, String prefix) throws java.io.IOException {
        Path path = Files.createTempFile(prefix, ".xlsx");
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
            return path;
        } catch (java.io.IOException exception) {
            try {
                Files.deleteIfExists(path);
            } catch (java.io.IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    private void readSheet(
        StylesTable styles,
        SharedStrings strings,
        InputStream input,
        BiConsumer<Integer, Map<Integer, String>> rowConsumer
    ) throws SAXException, ParserConfigurationException, java.io.IOException {
        XMLReader xmlReader = XMLHelper.newXMLReader();
        var handler = new RowHandler(rowConsumer);
        xmlReader.setContentHandler(new XSSFSheetXMLHandler(
            styles, null, strings, handler, new DataFormatter(Locale.KOREA), false
        ));
        xmlReader.parse(new InputSource(input));
    }

    private TransferApplicationRow parseApplication(int rowNumber, Map<Integer, String> values) {
        String applicantNumber = required(values, 3, "수험번호");
        return new TransferApplicationRow(
            rowNumber,
            requiredInteger(values, 0, "입학연도", 2000, 2100),
            applicantNumber,
            optional(values, 7),
            required(values, 8, "전형명"),
            optional(values, 9),
            required(values, 10, "모집단위명"),
            integer(values, 11, false)
        );
    }

    private TranscriptExcelRow parseCourse(int rowNumber, Map<Integer, String> values) {
        String applicantNumber = required(values, 2, "수험번호");
        int schoolYear = requiredInteger(values, 3, "학년", 1, 3);
        int semester = requiredInteger(values, 4, "학기", 1, 2);
        String organizationName = optional(values, 6);
        String courseName = required(values, 8, "과목명");
        BigDecimal credits = decimal(values, 9);
        if (credits == null || credits.signum() <= 0) {
            throw new IllegalArgumentException("이수단위는 0보다 커야 합니다.");
        }
        Integer grade = positiveInteger(values, 16);
        AchievementLevel achievement = achievement(optional(values, 17));
        Integer rank = positiveInteger(values, 10);
        Integer studentCount = positiveInteger(values, 11);
        Integer tiedRank = positiveInteger(values, 12);
        return new TranscriptExcelRow(
            rowNumber,
            applicantNumber,
            "미등록",
            null,
            null,
            null,
            schoolYear,
            semester,
            subjectCategory(organizationName == null ? "" : organizationName, courseName),
            courseName,
            grade,
            GradeScale.NINE_LEVEL,
            achievement,
            decimal(values, 13),
            decimal(values, 14),
            positiveDecimal(values, 15),
            studentCount,
            rank,
            tiedRank,
            null,
            credits,
            false,
            organizationName != null && isProfessional(organizationName)
        );
    }

    private List<String> warnings(int missingAssessmentRows) {
        List<String> warnings = new ArrayList<>();
        warnings.add("전달양식에 학생명이 없어 신규 지원자는 이름을 '미등록'으로 생성합니다.");
        if (missingAssessmentRows > 0) {
            warnings.add((
                "P·이수·기호 등 수치 환산할 수 없는 성적의 %,d개 행은 과목 이력만 저장되며 "
                    + "성적 계산과 최소 반영 과목 수 충족 여부에서 제외됩니다."
            ).formatted(missingAssessmentRows));
        }
        return List.copyOf(warnings);
    }

    private SubjectCategory subjectCategory(String organizationName, String courseName) {
        if (!organizationName.isBlank() && !isOrdinaryOrganization(organizationName)) {
            return SubjectCategory.OTHER;
        }
        if (isOtherOrganization(organizationName)) return SubjectCategory.OTHER;

        String value = organizationName + " " + courseName;
        if (value.contains("국어")) return SubjectCategory.KOREAN;
        if (value.contains("수학")) return SubjectCategory.MATH;
        if (value.contains("영어")) return SubjectCategory.ENGLISH;
        if (value.contains("과학")) return SubjectCategory.SCIENCE;
        if (value.contains("사회") || value.contains("역사") || value.contains("도덕") || value.contains("한국사")) {
            return SubjectCategory.SOCIAL;
        }
        return SubjectCategory.OTHER;
    }

    private boolean isOtherOrganization(String organizationName) {
        return organizationName.contains("기술") || organizationName.contains("가정")
            || organizationName.contains("제2외국어") || organizationName.contains("한문")
            || organizationName.contains("교양") || organizationName.contains("예술")
            || organizationName.contains("음악") || organizationName.contains("미술")
            || organizationName.contains("체육");
    }

    private boolean isProfessional(String organizationName) {
        return !organizationName.isBlank() && !isOrdinaryOrganization(organizationName);
    }

    private static boolean isOrdinaryOrganization(String organizationName) {
        String normalized = organizationName == null ? "" : organizationName.replaceAll("\\s", "");
        if (normalized.isBlank() || normalized.contains("에관한교과")) return false;
        return normalized.contains("국어") || normalized.contains("수학") || normalized.contains("영어")
            || normalized.contains("한국사") || normalized.contains("사회") || normalized.contains("역사")
            || normalized.contains("도덕") || normalized.contains("과학") || normalized.contains("체육")
            || normalized.contains("예술") || normalized.contains("음악") || normalized.contains("미술")
            || normalized.contains("기술") || normalized.contains("가정")
            || normalized.contains("제2외국어") || normalized.contains("한문")
            || normalized.contains("교양") || normalized.equals("정보")
            || normalized.contains("보통교과");
    }

    private void addCourseNature(Map<String, EnumSet<CourseNature>> formations, String key, CourseNature nature) {
        if (key == null || key.isBlank()) return;
        formations.computeIfAbsent(key.trim(), ignored -> EnumSet.noneOf(CourseNature.class)).add(nature);
    }

    private boolean isNonOrdinaryCourse(
        CourseSourceMetadata metadata,
        Map<String, EnumSet<CourseNature>> formationsByCourseCode,
        Map<String, EnumSet<CourseNature>> formationsByCourseName
    ) {
        if (metadata == null) return true;
        if (metadata.organizationName() != null && !metadata.organizationName().isBlank()) {
            return isProfessional(metadata.organizationName());
        }
        EnumSet<CourseNature> natures = metadata.courseCode() == null
            ? null : formationsByCourseCode.get(metadata.courseCode());
        if (natures == null || natures.isEmpty()) {
            natures = formationsByCourseName.get(metadata.courseName());
        }
        return natures == null || natures.isEmpty() || natures.contains(CourseNature.NON_ORDINARY);
    }

    private TranscriptExcelRow withProfessionalCourse(TranscriptExcelRow course, boolean professionalCourse) {
        return new TranscriptExcelRow(
            course.rowNumber(), course.applicantNumber(), course.studentName(), course.highSchoolCode(),
            course.highSchoolName(), course.graduationYear(), course.schoolYear(), course.semester(),
            course.subjectCategory(), course.courseName(), course.grade(), course.gradeScale(),
            course.achievement(), course.rawScore(), course.meanScore(), course.standardDeviation(),
            course.studentCount(), course.rankPosition(), course.tiedRankCount(), course.legacyAchievement(),
            course.credits(), course.careerSubject(), professionalCourse
        );
    }

    private enum CourseNature {
        ORDINARY,
        NON_ORDINARY
    }

    private record CourseSourceMetadata(String organizationName, String courseCode, String courseName) {}

    private AchievementLevel achievement(String value) {
        if (value == null) return null;
        try {
            return AchievementLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String required(Map<Integer, String> values, int index, String label) {
        String value = optional(values, index);
        if (value == null) throw new IllegalArgumentException(label + "이(가) 없습니다.");
        return value;
    }

    private String optional(Map<Integer, String> values, int index) {
        String value = values.get(index);
        if (value == null || value.isBlank() || "NULL".equalsIgnoreCase(value.trim())) return null;
        return value.trim();
    }

    private int requiredInteger(Map<Integer, String> values, int index, String label, int min, int max) {
        Integer value = integer(values, index, true);
        if (value == null || value < min || value > max) {
            throw new IllegalArgumentException(label + "은(는) " + min + "~" + max + " 사이여야 합니다.");
        }
        return value;
    }

    private Integer positiveInteger(Map<Integer, String> values, int index) {
        Integer value = integer(values, index, false);
        return value == null || value <= 0 ? null : value;
    }

    private Integer integer(Map<Integer, String> values, int index, boolean strict) {
        String value = optional(values, index);
        if (value == null) return null;
        try {
            return new BigDecimal(value.replace(",", "")).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            if (strict) throw new IllegalArgumentException("정수 값이 올바르지 않습니다: " + value);
            return null;
        }
    }

    private BigDecimal decimal(Map<Integer, String> values, int index) {
        String value = optional(values, index);
        if (value == null) return null;
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal positiveDecimal(Map<Integer, String> values, int index) {
        BigDecimal value = decimal(values, index);
        return value == null || value.signum() <= 0 ? null : value;
    }

    private void addError(List<TranscriptImportRowError> errors, int[] invalidRows, int row, String message) {
        invalidRows[0]++;
        if (errors.size() < MAX_REPORTED_ERRORS) {
            errors.add(new TranscriptImportRowError(row, message));
        }
    }

    private static final class RowHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final BiConsumer<Integer, Map<Integer, String>> consumer;
        private final Map<Integer, String> values = new HashMap<>();
        private int rowNumber;

        private RowHandler(BiConsumer<Integer, Map<Integer, String>> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void startRow(int rowNum) {
            rowNumber = rowNum;
            values.clear();
        }

        @Override
        public void endRow(int rowNum) {
            if (!values.isEmpty()) consumer.accept(rowNumber, Map.copyOf(values));
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            values.put(columnIndex(cellReference), formattedValue);
        }

        private int columnIndex(String reference) {
            int value = 0;
            for (int index = 0; index < reference.length(); index++) {
                char ch = reference.charAt(index);
                if (!Character.isLetter(ch)) break;
                value = value * 26 + Character.toUpperCase(ch) - 'A' + 1;
            }
            return value - 1;
        }
    }
}
