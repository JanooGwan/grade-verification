package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;

import com.jinhakapply.gradevalidation.admission.service.TukRecruitmentUnits;
import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.*;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportRowError;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** 수험번호로 두 시트를 연결하고 원천 행을 보존하는 스트리밍 파서. */
final class TukSourceExcelParser {
    static final String SOURCE_FORMAT = "TUK_SOURCE_WORKBOOK_V1";
    private static final List<String> APPLICATION_HEADERS = List.of(
        "수험번호", "전형명", "모집단위명", "계열", "고교유형", "졸업구분", "졸업연월");
    private static final List<String> COURSE_HEADERS = List.of("SUHUMNO", "GRADE", "TERM", "ORGANIZATIONNAME",
        "SUBJECTNAME", "ISUUNIT", "STDRANKINGGRADE", "ACHIEVEMENT", "HSBRANK", "SAMERANK", "STUDENTCOUNT",
        "SUBJECTSEPARATIONCODE", "CODENAME");
    private static final Set<String> TRACKS = Set.of("논술(논술우수자)", "학생부교과(교과우수자)",
        "학생부교과(지역균형)", "학생부교과(특성화고교졸업자)");
    private static final int MAX_ROWS = 500_000;

    static boolean supports(OPCPackage pkg) throws Exception {
        XSSFReader reader = new XSSFReader(pkg);
        var sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
        SharedStrings strings = reader.getSharedStringsTable();
        Set<String> found = new HashSet<>();
        while (sheets.hasNext()) {
            try (InputStream input = sheets.next()) {
                String name = sheets.getSheetName();
                List<String> required = name.equalsIgnoreCase("Sheet1") ? APPLICATION_HEADERS
                    : name.equalsIgnoreCase("Sheet2") ? COURSE_HEADERS : null;
                if (required == null) continue;
                readRows(input, strings, (row, values) -> {
                    if (values.values().containsAll(required)) found.add(name.toLowerCase(Locale.ROOT));
                }, true);
            }
        }
        return found.containsAll(Set.of("sheet1", "sheet2"));
    }

    TransferExcelParseResult parse(OPCPackage pkg, int year) throws Exception {
        var applications = new ArrayList<TransferApplicationRow>();
        var courses = new ArrayList<TranscriptExcelRow>();
        var profiles = new LinkedHashMap<String, TukApplicantProfile>();
        var errors = new ArrayList<TranscriptImportRowError>();
        var skipped = new ArrayList<TranscriptImportRowError>();
        int[] invalid = {0};
        int[] totalCourses = {0};
        int[] mappedUnits = {0};
        XSSFReader reader = new XSSFReader(pkg);
        var sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
        SharedStrings strings = reader.getSharedStringsTable();
        while (sheets.hasNext()) {
            try (InputStream input = sheets.next()) {
                String name = sheets.getSheetName();
                boolean applicantSheet = name.equalsIgnoreCase("Sheet1");
                if (!applicantSheet && !name.equalsIgnoreCase("Sheet2")) continue;
                var columns = new HashMap<String, Integer>();
                readRows(input, strings, (row, values) -> {
                    if (columns.isEmpty()) {
                        values.forEach((column, value) -> columns.put(value, column));
                        return;
                    }
                    if ((applicantSheet && applications.size() >= 50_000)
                        || (!applicantSheet && ++totalCourses[0] > MAX_ROWS)) {
                        throw CustomException.of(INVALID_TRANSCRIPT_FILE, "한국공학대 원본의 최대 처리 행 수를 초과했습니다.");
                    }
                    var fields = new HashMap<String, String>();
                    columns.forEach((key, column) -> fields.put(key, clean(values.get(column))));
                    try {
                        if (applicantSheet) {
                            String number = required(fields, "수험번호");
                            if (profiles.containsKey(number)) {
                                throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                                    "지원자 시트 " + row + "행에 중복 수험번호가 있습니다.");
                            }
                            String track = required(fields, "전형명");
                            String unit = required(fields, "모집단위명");
                            if (!TRACKS.contains(track)) throw new IllegalArgumentException("지원하지 않는 전형명입니다.");
                            String group = TukRecruitmentUnits.group(year, unit);
                            int otherYear = year == 2026 ? 2027 : 2026;
                            if (group == null && TukRecruitmentUnits.group(otherYear, unit) != null) {
                                throw new IllegalArgumentException(
                                    "선택한 %d학년도에 없는 %d학년도 모집단위입니다. 파일의 모집연도와 화면의 검증 기준연도를 확인해 주세요."
                                        .formatted(year, otherYear));
                            }
                            if (group == null || !Objects.equals(required(fields, "계열"), group.equals("공학계열") ? "공학" : "경영")) {
                                throw new IllegalArgumentException("모집단위와 계열을 확인해 주세요.");
                            }
                            TukApplicantProfile profile = profile(fields);
                            profiles.put(number, profile);
                            if (!Objects.equals(unit, TukRecruitmentUnits.nameForRuleYear(year, unit))) mappedUnits[0]++;
                            applications.add(new TransferApplicationRow(row, year, number, null, track, null, unit,
                                profile.graduationDate() == null ? null : profile.graduationDate().getYear(), "수시"));
                        } else courses.add(course(row, fields));
                    } catch (IllegalArgumentException exception) {
                        invalid[0]++;
                        if (errors.size() < 1_000) errors.add(new TranscriptImportRowError(row, exception.getMessage()));
                    }
                }, false);
            }
        }
        int unlinked = 0;
        var linked = new ArrayList<TranscriptExcelRow>();
        var withCourses = new HashSet<String>();
        for (TranscriptExcelRow course : courses) {
            if (!profiles.containsKey(course.applicantNumber())) {
                unlinked++;
                if (skipped.size() < 1_000) skipped.add(new TranscriptImportRowError(course.rowNumber(),
                    "지원자정보에 연결되지 않는 과목이므로 저장에서 제외했습니다."));
            } else { linked.add(course); withCourses.add(course.applicantNumber()); }
        }
        if (applications.isEmpty()) throw CustomException.of(INVALID_TRANSCRIPT_FILE, "유효한 지원자정보가 없습니다.");
        var warnings = new ArrayList<String>();
        warnings.add("검증 기준은 %d학년도 모집요강입니다. 과거 원본의 성적·졸업구분·졸업일은 변경하지 않습니다.".formatted(year));
        if (mappedUnits[0] > 0) warnings.add(
            "개편 전 모집단위 지원자 %,d건을 2027학년도 규칙에 연결했습니다. SW 자율전공→AI융합 자율전공, 전력응용시스템전공→전기공학전공, 미래에너지시스템전공→에너지공학전공. 원본 학과명은 보존합니다."
                .formatted(mappedUnits[0]));
        warnings.add("한국공학대 원본의 고교유형·졸업구분·졸업일을 반영합니다. 학생명은 제공되지 않아 신규 이름은 '미등록'입니다.");
        warnings.add("논술점수·검정고시 과목별 점수·학교폭력 정보는 이 파일에 없어 별도 확인이 필요합니다.");
        if (unlinked > 0) warnings.add("지원자정보에 연결되지 않는 과목 %,d건을 저장에서 제외했습니다.".formatted(unlinked));
        long missing = profiles.keySet().stream().filter(key -> !withCourses.contains(key)).count();
        if (missing > 0) warnings.add("과목 성적이 없는 지원자 %,d건은 출신 구분 및 비교내신 적용 여부 확인이 필요합니다.".formatted(missing));
        return new TransferExcelParseResult(SOURCE_FORMAT, List.copyOf(applications), List.copyOf(linked),
            invalid[0], unlinked, List.copyOf(skipped), List.copyOf(errors), List.copyOf(warnings), Map.copyOf(profiles));
    }

    private TukApplicantProfile profile(Map<String, String> f) {
        String school = required(f, "고교유형");
        EducationBackground background = switch (school) {
            case "검정고시 출신" -> EducationBackground.GED;
            case "국외고등학교출신" -> EducationBackground.FOREIGN_HIGH_SCHOOL;
            case "일반계고교", "특성화고교", "특수목적고교", "마이스터고", "기타" -> EducationBackground.DOMESTIC_HIGH_SCHOOL;
            default -> throw new IllegalArgumentException("고교유형을 확인해 주세요.");
        };
        HighSchoolType type = Set.of("특성화고교", "마이스터고").contains(school)
            ? HighSchoolType.SPECIALIZED : HighSchoolType.GENERAL;
        GraduationStatus status = switch (required(f, "졸업구분")) {
            case "졸업", "조기졸업" -> GraduationStatus.GRADUATE;
            case "졸업예정" -> GraduationStatus.EXPECTED_GRADUATE;
            case "해당없음" -> {
                if (background != EducationBackground.GED) throw new IllegalArgumentException("졸업구분을 확인해 주세요.");
                yield GraduationStatus.EXPECTED_GRADUATE;
            }
            default -> throw new IllegalArgumentException("지원하지 않는 졸업구분입니다.");
        };
        return new TukApplicantProfile(background, type, status,
            background == EducationBackground.GED ? null : graduationDate(f.get("졸업연월")));
    }

    static LocalDate graduationDate(String value) {
        if (value == null) return null;
        try {
            String number = new BigDecimal(value).stripTrailingZeros().toPlainString();
            LocalDate date = number.length() == 8
                ? LocalDate.parse(number, DateTimeFormatter.BASIC_ISO_DATE)
                : DateUtil.getLocalDateTime(Double.parseDouble(number)).toLocalDate();
            if (date.getYear() < 1900 || date.getYear() > 2100) throw new IllegalArgumentException();
            return date;
        } catch (RuntimeException exception) { throw new IllegalArgumentException("졸업연월은 유효한 날짜여야 합니다."); }
    }

    private TranscriptExcelRow course(int row, Map<String, String> f) {
        int year = bounded(f.get("GRADE"), 1, 3, "학년");
        int term = bounded(f.get("TERM"), 1, 2, "학기");
        BigDecimal credits = new BigDecimal(required(f, "ISUUNIT"));
        if (credits.signum() <= 0) throw new IllegalArgumentException("이수단위는 0보다 커야 합니다.");
        String separation = required(f, "SUBJECTSEPARATIONCODE");
        if (!Set.of("일반", "진로", "예체능").contains(separation)) throw new IllegalArgumentException("과목 구분을 확인해 주세요.");
        Integer grade = numericGrade(f.get("STDRANKINGGRADE"));
        String rawAchievement = f.get("ACHIEVEMENT");
        AchievementLevel achievement = rawAchievement != null && Set.of("A", "B", "C", "D", "E").contains(rawAchievement)
            ? AchievementLevel.valueOf(rawAchievement) : null;
        SubjectCategory category = category(f.get("CODENAME"), f.get("ORGANIZATIONNAME"));
        return new TranscriptExcelRow(row, required(f, "SUHUMNO"), "미등록", null, null, null,
            year, term, category, required(f, "SUBJECTNAME"), grade, GradeScale.NINE_LEVEL, achievement,
            null, null, null, positive(f.get("STUDENTCOUNT")), positive(f.get("HSBRANK")), positive(f.get("SAMERANK")),
            null, credits, separation.equals("진로"), false);
    }

    static SubjectCategory category(String code, String organization) {
        String value = code != null ? code : organization == null ? "" : organization;
        return switch (value) {
            case "국어" -> SubjectCategory.KOREAN;
            case "수학" -> SubjectCategory.MATH;
            case "영어" -> SubjectCategory.ENGLISH;
            case "사회", "한국사", "사회(역사/도덕포함)" -> SubjectCategory.SOCIAL;
            case "과학", "과학 계열", "과학에 관한 교과" -> SubjectCategory.SCIENCE;
            default -> SubjectCategory.OTHER;
        };
    }
    private static Integer numericGrade(String value) {
        if (value == null || Set.of("P", "·", "우수", "보통", "미흡", "이수").contains(value)) return null;
        return bounded(value, 1, 9, "석차등급");
    }
    private static Integer positive(String value) {
        if (value == null) return null;
        int number = new BigDecimal(value).intValueExact();
        if (number == 0) return null;
        if (number < 0) throw new IllegalArgumentException("석차와 인원은 음수일 수 없습니다.");
        return number;
    }
    private static int bounded(String value, int min, int max, String label) {
        try {
            int n = new BigDecimal(value).intValueExact();
            if (n >= min && n <= max) return n;
        } catch (RuntimeException ignored) { }
        throw new IllegalArgumentException(label + " 범위를 확인해 주세요.");
    }
    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) throw new IllegalArgumentException(key + " 값이 없습니다.");
        return value;
    }
    private static String clean(String value) {
        if (value == null || value.isBlank() || value.trim().equals("[NULL]")) return null;
        return value.trim();
    }

    // 서식이 날짜인 20260201 같은 숫자를 DataFormatter가 변형하지 않도록 원시 값을 읽는다.
    private static void readRows(InputStream input, SharedStrings strings,
        BiConsumer<Integer, Map<Integer, String>> consumer, boolean headerOnly) throws Exception {
        XMLReader xml = XMLHelper.newXMLReader();
        xml.setContentHandler(new DefaultHandler() {
            Map<Integer, String> values;
            int row, column;
            String type;
            StringBuilder value;
            boolean collecting;
            @Override public void startElement(String uri, String local, String name, Attributes attributes) {
                if (local.equals("row")) { values = new HashMap<>(); row = Integer.parseInt(attributes.getValue("r")); }
                if (local.equals("c")) {
                    column = 0;
                    for (char ch : attributes.getValue("r").toCharArray()) {
                        if (!Character.isLetter(ch)) break;
                        column = column * 26 + ch - 'A' + 1;
                    }
                    type = attributes.getValue("t"); value = new StringBuilder();
                }
                if (local.equals("v") || local.equals("t")) collecting = true;
            }
            @Override public void characters(char[] ch, int start, int length) {
                if (collecting) value.append(ch, start, length);
            }
            @Override public void endElement(String uri, String local, String name) throws SAXException {
                if (local.equals("v") || local.equals("t")) collecting = false;
                if (local.equals("c")) {
                    String text = value.toString();
                    if ("s".equals(type)) text = strings.getItemAt(Integer.parseInt(text)).getString();
                    if (clean(text) != null) values.put(column - 1, text.trim());
                }
                if (local.equals("row") && !values.isEmpty()) {
                    consumer.accept(row, values);
                    if (headerOnly) throw new HeaderComplete();
                }
            }
        });
        try { xml.parse(new InputSource(input)); } catch (HeaderComplete expected) { }
    }
    private static final class HeaderComplete extends SAXException { }
}
