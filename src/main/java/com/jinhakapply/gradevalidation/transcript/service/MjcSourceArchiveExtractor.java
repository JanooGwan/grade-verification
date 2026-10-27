package com.jinhakapply.gradevalidation.transcript.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.springframework.stereotype.Component;

@Component
class MjcSourceArchiveExtractor {
    static final String SOURCE_FORMAT = "MJC_CURRENT_SOURCE_ZIP_V1";
    private static final long MAX_EXPANDED_BYTES = 1024L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("applicants", "raw_student_records", "raw_student_subjects",
        "raw_ged_records", "raw_ged_subjects", "raw_attendance", "current_subject_overrides", "raw_evaluation_scores");
    private static final Pattern PART_FILE = Pattern.compile("parts/current-part-(\\d{4})-([a-z_]+)\\.csv");
    private static final List<String> APPLICANT_HEADERS = List.of("examNumber", "admissionTypeCode", "admissionTypeName",
        "recruitmentUnitCode", "recruitmentUnitName", "highSchoolCode", "graduationDate", "graduationStatus", "sourceApplicantId");
    private static final List<String> BASE_HEADERS = List.of("examNumber", "graduateYear", "graduateGrade",
        "specializedSchoolYN", "applicantScCode", "sourceApplicantId");
    private static final List<String> COURSE_HEADERS = List.of("examNumber", "grade", "term", "organizationName",
        "subjectName", "unit", "rankingGrade", "achievement", "originalScore", "avgScore", "standardDeviation",
        "studentCount", "rank", "sameRank", "subjectSeparationCode", "sourceApplicantId", "sourceStudentRecordId",
        "sourceSubjectId", "sourcePart");

    Bundle extract(Path archive, Path directory, int admissionYear) {
        Map<String, Path> files = unpack(archive, directory);
        var execution = rows(requiredFile(files, "execution.csv"));
        if (execution.size() != 1 || !"CURRENT_RECEIVED_SOURCE".equals(execution.getFirst().get("source_basis"))
            || !"MJC".equals(execution.getFirst().get("university_code"))
            || integer(execution.getFirst(), "admission_year") != admissionYear) {
            throw new IllegalArgumentException("명지전문대 현재 수신 원자료 ZIP과 선택한 학년도가 일치해야 합니다.");
        }
        Map<String, IndexRow> index = new LinkedHashMap<>();
        for (var row : rows(requiredFile(files, "file-index.csv"))) {
            String name = required(row, "file");
            if (!name.startsWith("parts/") || !name.endsWith(".csv")) continue;
            var matcher = PART_FILE.matcher(name);
            if (!matcher.matches() || !TYPES.contains(matcher.group(2))) throw MjcArchiveCsv.invalid();
            var item = new IndexRow(Integer.parseInt(matcher.group(1)), matcher.group(2), integer(row, "data_rows"),
                required(row, "admission_type_code"));
            if (item.part() != integer(row, "part") || item.rows() > 200_000
                || index.putIfAbsent(name, item) != null) throw MjcArchiveCsv.invalid();
            requiredFile(files, name);
        }
        Set<Integer> parts = new HashSet<>();
        index.values().forEach(item -> parts.add(item.part()));
        int expectedParts = integer(execution.getFirst(), "parts");
        if (expectedParts < 1 || expectedParts > 100 || parts.size() != expectedParts) throw MjcArchiveCsv.invalid();
        for (int part = 1; part <= expectedParts; part++) {
            for (String type : TYPES) if (!index.containsKey(partName(part, type))) throw MjcArchiveCsv.invalid();
        }
        for (String name : files.keySet()) {
            if (PART_FILE.matcher(name).matches() && !index.containsKey(name)) throw MjcArchiveCsv.invalid();
        }
        Path applicants = directory.resolve("canonical-applicants.csv");
        Path bases = directory.resolve("canonical-bases.csv");
        Path courses = directory.resolve("canonical-courses.csv");
        List<MjcArchiveApplicant> metadata = new ArrayList<>();
        Set<String> allApplicantIds = new HashSet<>();
        Set<String> examNumbers = new HashSet<>();
        try (var applicantWriter = Files.newBufferedWriter(applicants);
             var baseWriter = Files.newBufferedWriter(bases);
             var courseWriter = Files.newBufferedWriter(courses)) {
            MjcArchiveCsv.write(applicantWriter, APPLICANT_HEADERS);
            MjcArchiveCsv.write(baseWriter, BASE_HEADERS);
            MjcArchiveCsv.write(courseWriter, COURSE_HEADERS);
            for (int part = 1; part <= expectedParts; part++) {
                readPart(files, index, part, applicantWriter, baseWriter, courseWriter, metadata, allApplicantIds, examNumbers);
            }
        } catch (IOException exception) { throw MjcArchiveCsv.invalid(); }
        if (metadata.size() != integer(execution.getFirst(), "applicant_count")) throw MjcArchiveCsv.invalid();
        return new Bundle(applicants, bases, courses, List.copyOf(metadata));
    }

    private void readPart(Map<String, Path> files, Map<String, IndexRow> index, int part,
        BufferedWriter applicantWriter, BufferedWriter baseWriter, BufferedWriter courseWriter,
        List<MjcArchiveApplicant> metadata, Set<String> allIds, Set<String> exams) throws IOException {
        Map<String, Map<String, String>> applicants = new LinkedHashMap<>();
        read(files, index, part, "applicants", row -> {
            String id = required(row, "id");
            if (!allIds.add(id) || !exams.add(required(row, "examNumber"))) {
                throw new IllegalArgumentException("지원자 ID 또는 수험번호가 중복되어 자동 연결할 수 없습니다. 원자료 구성을 확인해 주세요.");
            }
            applicants.put(id, row);
        });
        Map<String, List<Map<String, String>>> bases = grouped(files, index, part, "raw_student_records", applicants);
        Map<String, List<Map<String, String>>> geds = grouped(files, index, part, "raw_ged_records", applicants);
        Map<String, List<Map<String, String>>> gedSubjects = new HashMap<>();
        Map<String, Set<String>> knownBaseIds = ids(bases);
        Map<String, Set<String>> knownGedIds = ids(geds);
        Map<String, Integer> courseCounts = new HashMap<>();
        read(files, index, part, "raw_ged_subjects", row -> {
            String id = link(row, applicants);
            if (!knownGedIds.getOrDefault(id, Set.of()).contains(required(row, "gedRecordId"))) throw MjcArchiveCsv.invalid();
            gedSubjects.computeIfAbsent(id, ignored -> new ArrayList<>()).add(row);
        });
        Map<String, MjcArchiveApplicant> byId = new LinkedHashMap<>();
        for (var entry : applicants.entrySet()) {
            String id = entry.getKey();
            var applicant = entry.getValue();
            var studentRecords = bases.getOrDefault(id, List.of());
            var gedRecords = geds.getOrDefault(id, List.of());
            checkCount(applicant, "raw_student_records", studentRecords.size());
            checkCount(applicant, "raw_ged_records", gedRecords.size());
            checkCount(applicant, "raw_ged_subjects", gedSubjects.getOrDefault(id, List.of()).size());
            String type = academicType(applicant, studentRecords, gedRecords);
            String issue = null;
            String studentId = studentRecords.size() == 1 ? required(studentRecords.getFirst(), "id") : null;
            List<Map<String, String>> validGeds = gedRecords.stream().filter(row -> "true".equals(row.get("isValid"))).toList();
            String gedId = validGeds.size() == 1 ? required(validGeds.getFirst(), "id") : null;
            if ("DOMESTIC_HIGH_SCHOOL".equals(type) && studentRecords.size() != 1) {
                issue = studentRecords.isEmpty() ? "STUDENT_RECORD_MISSING" : "STUDENT_RECORD_MULTIPLE";
            } else if ("GED".equals(type) && validGeds.size() != 1) {
                issue = validGeds.isEmpty() ? "VALID_GED_RECORD_MISSING" : "VALID_GED_RECORD_MULTIPLE";
            } else if ("UNRESOLVED".equals(type)) issue = "ACADEMIC_TYPE_UNRESOLVED";
            List<MjcArchiveApplicant.GedSubject> selectedSubjects = new ArrayList<>();
            if (gedId != null) {
                for (var subject : gedSubjects.getOrDefault(id, List.of())) {
                    if (gedId.equals(subject.get("gedRecordId"))) {
                        try {
                            var score = new java.math.BigDecimal(required(subject, "subjectScore"));
                            if (score.signum() < 0 || score.compareTo(new java.math.BigDecimal("100")) > 0) throw MjcArchiveCsv.invalid();
                            selectedSubjects.add(new MjcArchiveApplicant.GedSubject(required(subject, "id"),
                                required(subject, "subjectName"), score));
                        } catch (NumberFormatException exception) { throw MjcArchiveCsv.invalid(); }
                    }
                }
            }
            var data = new MjcArchiveApplicant(id, required(applicant, "examNumber"), part, type, studentId, gedId,
                issue, gedId == null ? null : validGeds.getFirst().get("acceptedDate"), List.copyOf(selectedSubjects));
            byId.put(id, data);
            var normalized = new LinkedHashMap<>(applicant);
            normalized.put("sourceApplicantId", id);
            MjcArchiveCsv.write(applicantWriter, APPLICANT_HEADERS.stream().map(normalized::get).toList());
            var base = new LinkedHashMap<String, String>();
            if (studentRecords.size() == 1) base.putAll(studentRecords.getFirst());
            base.put("examNumber", data.examNumber());
            base.put("sourceApplicantId", id);
            MjcArchiveCsv.write(baseWriter, BASE_HEADERS.stream().map(base::get).toList());
        }
        Set<String> subjectIds = new HashSet<>();
        read(files, index, part, "raw_student_subjects", row -> {
            String id = link(row, applicants);
            String recordId = required(row, "studentBaseInfoId");
            if (!knownBaseIds.getOrDefault(id, Set.of()).contains(recordId)
                || !subjectIds.add(required(row, "id"))) throw MjcArchiveCsv.invalid();
            courseCounts.merge(id, 1, Integer::sum);
            var source = byId.get(id);
            // Keep every original subject for audit. The applicant's issue blocks ambiguous calculation.
            var normalized = new LinkedHashMap<>(row);
            normalized.put("examNumber", source.examNumber());
            normalized.put("sourceApplicantId", id);
            normalized.put("sourceStudentRecordId", recordId);
            normalized.put("sourceSubjectId", row.get("id"));
            normalized.put("sourcePart", Integer.toString(part));
            try { MjcArchiveCsv.write(courseWriter, COURSE_HEADERS.stream().map(normalized::get).toList()); }
            catch (IOException exception) { throw MjcArchiveCsv.invalid(); }
        });
        for (var entry : applicants.entrySet()) {
            checkCount(entry.getValue(), "raw_student_subjects", courseCounts.getOrDefault(entry.getKey(), 0));
        }
        // These are deliberately outside this score calculation, but validate file completeness and linkage.
        for (String type : List.of("raw_attendance", "current_subject_overrides", "raw_evaluation_scores")) {
            read(files, index, part, type, row -> link(row, applicants));
        }
        metadata.addAll(byId.values());
    }

    private String academicType(Map<String, String> applicant, List<?> bases, List<?> geds) {
        String declared = applicant.get("academicRecordType");
        if (declared != null && !declared.isBlank()) {
            return switch (declared) {
                case "DOMESTIC_HIGH_SCHOOL", "GED", "FOREIGN_HIGH_SCHOOL" -> declared;
                default -> "UNRESOLVED"; // Unknown code meanings must not be guessed from a numeric code.
            };
        }
        if (!bases.isEmpty() && geds.isEmpty()) return "DOMESTIC_HIGH_SCHOOL";
        if (bases.isEmpty() && !geds.isEmpty()) return "GED";
        return "UNRESOLVED";
    }

    private Map<String, List<Map<String, String>>> grouped(Map<String, Path> files, Map<String, IndexRow> index,
        int part, String type, Map<String, Map<String, String>> applicants) {
        Map<String, List<Map<String, String>>> result = new HashMap<>();
        read(files, index, part, type, row -> result.computeIfAbsent(link(row, applicants), ignored -> new ArrayList<>()).add(row));
        return result;
    }

    private Map<String, Set<String>> ids(Map<String, List<Map<String, String>>> records) {
        Map<String, Set<String>> result = new HashMap<>();
        records.forEach((id, rows) -> {
            Set<String> values = new HashSet<>();
            for (var row : rows) if (!values.add(required(row, "id"))) throw MjcArchiveCsv.invalid();
            result.put(id, values);
        });
        return result;
    }

    private String link(Map<String, String> row, Map<String, Map<String, String>> applicants) {
        String id = required(row, "applicant_id");
        var applicant = applicants.get(id);
        if (applicant == null || !required(row, "exam_number").equals(applicant.get("examNumber"))
            || !required(row, "admission_type_code").equals(applicant.get("admissionTypeCode"))
            || !required(row, "recruitment_unit_code").equals(applicant.get("recruitmentUnitCode"))) throw MjcArchiveCsv.invalid();
        return id;
    }

    private void read(Map<String, Path> files, Map<String, IndexRow> index, int part, String type,
        java.util.function.Consumer<Map<String, String>> consumer) {
        String name = partName(part, type);
        int[] count = {0};
        List<String> requiredHeaders = type.equals("applicants")
            ? List.of("id", "examNumber", "admissionTypeCode", "admissionTypeName", "recruitmentUnitCode", "recruitmentUnitName")
            : List.of("applicant_id", "exam_number", "admission_type_code", "recruitment_unit_code", "id");
        MjcArchiveCsv.read(requiredFile(files, name), requiredHeaders, raw -> {
            Map<String, String> row = new LinkedHashMap<>();
            raw.forEach((key, value) -> row.put(key, MjcArchiveCsv.decode(value)));
            if (++count[0] > 200_000) throw MjcArchiveCsv.invalid();
            String code = type.equals("applicants") ? required(row, "admissionTypeCode") : required(row, "admission_type_code");
            if (!code.equals(index.get(name).admissionCode())) throw MjcArchiveCsv.invalid();
            consumer.accept(row);
        });
        if (count[0] != index.get(name).rows()) throw MjcArchiveCsv.invalid();
    }

    private Map<String, Path> unpack(Path archive, Path directory) {
        Map<String, Path> result = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        long total = 0;
        try (ZipFile zip = new ZipFile(archive.toFile(), java.nio.charset.StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            int count = 0;
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (++count > 1500) throw MjcArchiveCsv.invalid();
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains(":")
                    || Arrays.asList(name.split("/")).contains("..") || !names.add(name)) throw MjcArchiveCsv.invalid();
                if (entry.isDirectory()) continue;
                // Accept a single enclosing source folder without using archive paths as filesystem targets.
                String logical = name;
                if (!(logical.equals("execution.csv") || logical.equals("file-index.csv") || logical.startsWith("parts/"))) {
                    int separator = logical.indexOf('/');
                    if (separator >= 0) logical = logical.substring(separator + 1);
                }
                boolean relevant = logical.equals("execution.csv") || logical.equals("file-index.csv") || PART_FILE.matcher(logical).matches();
                // Read and bound ignored members too, so archive expansion is bounded as a whole.
                Path target = relevant ? directory.resolve("entry-" + count + ".csv") : null;
                if (relevant && result.putIfAbsent(logical, target) != null) throw MjcArchiveCsv.invalid();
                try (InputStream input = zip.getInputStream(entry);
                     var output = target == null ? java.io.OutputStream.nullOutputStream() : Files.newOutputStream(target)) {
                    byte[] buffer = new byte[65536];
                    int size;
                    while ((size = input.read(buffer)) != -1) {
                        total += size;
                        if (total > MAX_EXPANDED_BYTES) throw new IllegalArgumentException("ZIP 해제 크기는 1GB 이하여야 합니다.");
                        output.write(buffer, 0, size);
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("ZIP을 읽지 못했습니다. 암호화 ZIP은 먼저 해제한 뒤 폴더를 일반 ZIP으로 다시 압축해 주세요.");
        }
        return result;
    }

    private List<Map<String, String>> rows(Path path) {
        List<Map<String, String>> result = new ArrayList<>();
        MjcArchiveCsv.read(path, raw -> {
            if (result.size() >= 1500) throw MjcArchiveCsv.invalid();
            Map<String, String> row = new LinkedHashMap<>();
            raw.forEach((key, value) -> row.put(key, MjcArchiveCsv.decode(value)));
            result.add(row);
        });
        return result;
    }

    private void checkCount(Map<String, String> row, String key, int count) {
        if (integer(row, key) != count) throw MjcArchiveCsv.invalid();
    }
    private static String partName(int part, String type) { return "parts/current-part-%04d-%s.csv".formatted(part, type); }
    private static Path requiredFile(Map<String, Path> files, String name) {
        Path result = files.get(name);
        if (result == null) throw new IllegalArgumentException("ZIP에 필수 원자료 파일이 없습니다. execution.csv, file-index.csv와 parts 폴더를 함께 포함해 주세요.");
        return result;
    }
    private static String required(Map<String, String> row, String key) {
        String value = row.get(key);
        if (value == null || value.isBlank()) throw MjcArchiveCsv.invalid();
        return value;
    }
    private static int integer(Map<String, String> row, String key) {
        try {
            int value = Integer.parseInt(required(row, key));
            if (value < 0) throw MjcArchiveCsv.invalid();
            return value;
        } catch (NumberFormatException exception) { throw MjcArchiveCsv.invalid(); }
    }

    private record IndexRow(int part, String type, int rows, String admissionCode) {}
    record Bundle(Path applicantsFile, Path baseInfoFile, Path subjectScoreFile, List<MjcArchiveApplicant> applicants) {}
}
