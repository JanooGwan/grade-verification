package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MjcSourceArchiveExtractorTest {
    @TempDir Path directory;
    private final MjcSourceArchiveExtractor extractor = new MjcSourceArchiveExtractor();
    private static final List<String> TYPES = List.of("applicants", "raw_student_records", "raw_student_subjects",
        "raw_attendance", "current_subject_overrides", "raw_ged_records", "raw_ged_subjects", "raw_evaluation_scores");
    private static final String COMMON = "applicant_id,exam_number,admission_type_code,recruitment_unit_code,id";

    @Test
    void combinesTwelvePartsWithNoExcelRowLimitAndKeepsSourceIds() throws Exception {
        var bundle = extractor.extract(zip(fixture(12), "source/"), output(), 2027);
        assertThat(bundle.applicants()).hasSize(12);
        assertThat(bundle.applicants().getFirst().issue()).isNull();
        assertThat(bundle.applicants().getFirst().examNumber()).isEqualTo("00000001");
        List<MjcSourceCsvReader.CourseRow> courses = new ArrayList<>();
        var count = new MjcSourceCsvReader().streamCourses(bundle.subjectScoreFile(), 3, courses::addAll);
        assertThat(count.totalRows()).isEqualTo(12);
        assertThat(courses.getFirst().sourceApplicantId()).isEqualTo("A1");
        assertThat(courses.getFirst().sourceStudentRecordId()).isEqualTo("B1");
        assertThat(courses.getFirst().sourceSubjectId()).isEqualTo("C1");
    }

    @Test
    void acceptsProducerStudentRecordCodeAcrossAllTwelveParts() throws Exception {
        var files = fixture(12);
        files.replaceAll((name, content) -> name.endsWith("-applicants.csv")
            ? content.replace("DOMESTIC_HIGH_SCHOOL", "STUDENT_RECORD") : content);
        var bundle = extractor.extract(zip(files, ""), output(), 2027);
        assertThat(bundle.applicants()).hasSize(12).allSatisfy(applicant -> {
            assertThat(applicant.academicType()).isEqualTo("DOMESTIC_HIGH_SCHOOL");
            assertThat(applicant.studentRecordId()).isNotNull();
            assertThat(applicant.issue()).isNull();
        });
        assertThat(MjcArchiveInputIssues.summary(bundle.applicants())).isNull();
    }

    @Test
    void unknownClassificationWithLinkedStudentRecordIsNotReportedAsMissingRecord() throws Exception {
        var files = fixture(1);
        files.replaceAll((name, content) -> name.endsWith("-applicants.csv")
            ? content.replace("DOMESTIC_HIGH_SCHOOL", "SYNTHETIC_UNKNOWN_CODE") : content);
        var bundle = extractor.extract(zip(files, ""), output(), 2027);
        assertThat(bundle.applicants().getFirst().studentRecordId()).isEqualTo("B1");
        assertThat(bundle.applicants().getFirst().issue()).isEqualTo("ACADEMIC_TYPE_UNRESOLVED");
        assertThat(MjcArchiveInputIssues.summary(bundle.applicants()))
            .contains("학력 분류 코드 인식 필요 1건", "원본 자료가 없다는 뜻이 아닙니다")
            .doesNotContain("학생부 원본 미연결", "SYNTHETIC_UNKNOWN_CODE");
    }

    @Test
    void rejectsMissingPartAndWrongYear() throws Exception {
        var files = fixture(12);
        files.remove(part(12, "raw_student_subjects"));
        assertThatThrownBy(() -> extractor.extract(zip(files, ""), output(), 2027)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> extractor.extract(zip(fixture(1), ""), output(), 2026)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWrongRowCountsAndCrossApplicantLinksWithoutLeakingValues() throws Exception {
        var files = fixture(1);
        files.put(part(1, "raw_student_subjects"), files.get(part(1, "raw_student_subjects"))
            .replace("A1,00000001", "SYNTHETIC-SECRET,00000001"));
        Path brokenLink = zip(files, "");
        assertThatThrownBy(() -> extractor.extract(brokenLink, output(), 2027))
            .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("SYNTHETIC-SECRET");
        files = fixture(1);
        files.put("file-index.csv", files.get("file-index.csv").replace("raw_student_subjects.csv,1", "raw_student_subjects.csv,2"));
        Path archive = zip(files, "");
        assertThatThrownBy(() -> extractor.extract(archive, output(), 2027)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTraversalAndDuplicateLogicalEntries() throws Exception {
        var files = fixture(1);
        files.put("../outside.csv", "must-not-write");
        Path traversal = zip(files, "");
        assertThatThrownBy(() -> extractor.extract(traversal, output(), 2027)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.exists(directory.resolve("outside.csv"))).isFalse();
        files = fixture(1);
        files.put("second/execution.csv", files.get("execution.csv"));
        Path archive = zip(files, "");
        assertThatThrownBy(() -> extractor.extract(archive, output(), 2027)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsApplicantWithoutStudentRecordAsUnscoredInsteadOfDroppingApplicant() throws Exception {
        var files = fixture(1);
        files.put(part(1, "applicants"), files.get(part(1, "applicants")).replace("DOMESTIC_HIGH_SCHOOL,1,1,0,0", "DOMESTIC_HIGH_SCHOOL,0,0,0,0"));
        files.put(part(1, "raw_student_records"), COMMON + ",graduateYear,graduateGrade,specializedSchoolYN,applicantScCode\r\n");
        files.put(part(1, "raw_student_subjects"), subjectHeader() + "\r\n");
        files.put("file-index.csv", files.get("file-index.csv").replace("raw_student_records.csv,1", "raw_student_records.csv,0")
            .replace("raw_student_subjects.csv,1", "raw_student_subjects.csv,0"));
        var bundle = extractor.extract(zip(files, ""), output(), 2027);
        assertThat(bundle.applicants()).singleElement().satisfies(applicant ->
            assertThat(applicant.issue()).isEqualTo("STUDENT_RECORD_MISSING"));
        assertThat(new MjcSourceCsvReader().readApplicants(bundle.applicantsFile())).hasSize(1);
    }

    @Test
    void restoresMultilineQuotedStringsNullLiteralBackslashAndSafetyPrefix() throws Exception {
        Path csv = directory.resolve("quoted.csv");
        Files.writeString(csv, "\uFEFFa,b,c,d,e\r\n\"line1\r\nline2,\"\"quote\"\"\",\\N,\\\\N,'=literal,0\r\n");
        List<Map<String, String>> rows = new ArrayList<>();
        MjcArchiveCsv.read(csv, rows::add);
        assertThat(rows.getFirst().get("a")).isEqualTo("line1\r\nline2,\"quote\"");
        assertThat(MjcArchiveCsv.decode(rows.getFirst().get("b"))).isNull();
        assertThat(MjcArchiveCsv.decode(rows.getFirst().get("c"))).isEqualTo("\\N");
        assertThat(MjcArchiveCsv.decode(rows.getFirst().get("d"))).isEqualTo("=literal");
        assertThat(MjcArchiveCsv.decode(rows.getFirst().get("e"))).isEqualTo("0");
    }

    @Test
    void multipleStudentRecordsKeepAllSubjectsButBlockAutomaticCalculation() throws Exception {
        var files = fixture(1);
        files.put(part(1, "applicants"), files.get(part(1, "applicants"))
            .replace("DOMESTIC_HIGH_SCHOOL,1,1,0,0", "DOMESTIC_HIGH_SCHOOL,2,2,0,0"));
        files.put(part(1, "raw_student_records"), files.get(part(1, "raw_student_records"))
            + "A1,00000001,0105,UNIT,B2,2027,3,N,1\r\n");
        files.put(part(1, "raw_student_subjects"), files.get(part(1, "raw_student_subjects"))
            + "A1,00000001,0105,UNIT,C2,B2,1,1,국어,두번째원본,3,1,\\N,100,80,10,100,\\N,\\N,01\r\n");
        files.put("file-index.csv", files.get("file-index.csv").replace("raw_student_records.csv,1", "raw_student_records.csv,2")
            .replace("raw_student_subjects.csv,1", "raw_student_subjects.csv,2"));
        var bundle = extractor.extract(zip(files, ""), output(), 2027);
        assertThat(bundle.applicants().getFirst().issue()).isEqualTo("STUDENT_RECORD_MULTIPLE");
        assertThat(new MjcSourceCsvReader().streamCourses(bundle.subjectScoreFile(), 5, rows -> { }).totalRows()).isEqualTo(2);
    }

    @Test
    void usesOnlyOneValidGedRecordAndPreservesZeroScore() throws Exception {
        var files = fixture(1);
        files.put(part(1, "applicants"), files.get(part(1, "applicants"))
            .replace("DOMESTIC_HIGH_SCHOOL,1,1,0,0", "GED,0,0,2,2"));
        files.put(part(1, "raw_student_records"), COMMON + "\r\n");
        files.put(part(1, "raw_student_subjects"), subjectHeader() + "\r\n");
        files.put(part(1, "raw_ged_records"), COMMON + ",isValid,acceptedDate\r\n"
            + "A1,00000001,0105,UNIT,G1,true,2026-08-01\r\nA1,00000001,0105,UNIT,G2,false,2025-08-01\r\n");
        files.put(part(1, "raw_ged_subjects"), COMMON + ",gedRecordId,subjectName,subjectScore\r\n"
            + "A1,00000001,0105,UNIT,S1,G1,국어,0\r\nA1,00000001,0105,UNIT,S2,G2,국어,100\r\n");
        files.put("file-index.csv", files.get("file-index.csv").replace("raw_student_records.csv,1", "raw_student_records.csv,0")
            .replace("raw_student_subjects.csv,1", "raw_student_subjects.csv,0")
            .replace("raw_ged_records.csv,0", "raw_ged_records.csv,2")
            .replace("raw_ged_subjects.csv,0", "raw_ged_subjects.csv,2"));
        var source = extractor.extract(zip(files, ""), output(), 2027).applicants().getFirst();
        assertThat(source.issue()).isNull();
        assertThat(source.gedRecordId()).isEqualTo("G1");
        assertThat(source.gedSubjects()).singleElement().satisfies(subject -> assertThat(subject.score()).isZero());
    }

    @Test
    void literalNullMarkerInSubjectNameSurvivesCanonicalConversion() throws Exception {
        var files = fixture(1);
        files.put(part(1, "raw_student_subjects"), files.get(part(1, "raw_student_subjects")).replace("합성과목", "\\\\N"));
        var bundle = extractor.extract(zip(files, ""), output(), 2027);
        List<MjcSourceCsvReader.CourseRow> courses = new ArrayList<>();
        new MjcSourceCsvReader().streamCourses(bundle.subjectScoreFile(), 10, courses::addAll);
        assertThat(courses.getFirst().courseName()).isEqualTo("\\N");
    }

    private Path output() throws Exception { return Files.createTempDirectory(directory, "extract-"); }
    private Path zip(Map<String, String> files, String prefix) throws Exception {
        Path target = Files.createTempFile(directory, "synthetic-", ".zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(target))) {
            for (var file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(prefix + file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return target;
    }
    private static String part(int number, String type) { return "parts/current-part-%04d-%s.csv".formatted(number, type); }
    private static String subjectHeader() {
        return COMMON + ",studentBaseInfoId,grade,term,organizationName,subjectName,unit,rankingGrade,achievement,originalScore,avgScore,standardDeviation,studentCount,rank,sameRank,subjectSeparationCode";
    }
    static Map<String, String> fixture(int parts) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("execution.csv", "source_basis,university_code,admission_year,parts,applicant_count\r\nCURRENT_RECEIVED_SOURCE,MJC,2027," + parts + "," + parts + "\r\n");
        StringBuilder index = new StringBuilder("part,file,data_rows,admission_type_code\r\n");
        for (int number = 1; number <= parts; number++) {
            String exam = "%08d".formatted(number);
            String common = "A" + number + "," + exam + ",0105,UNIT,";
            for (String type : TYPES) {
                int rows = SetHolder.NONEMPTY.contains(type) ? 1 : 0;
                index.append(number).append(',').append(part(number, type)).append(',').append(rows).append(",0105\r\n");
                files.put(part(number, type), COMMON + "\r\n");
            }
            files.put(part(number, "applicants"),
                "id,examNumber,admissionTypeCode,admissionTypeName,recruitmentUnitCode,recruitmentUnitName,highSchoolCode,graduationDate,graduationStatus,academicRecordType,raw_student_records,raw_student_subjects,raw_ged_records,raw_ged_subjects\r\n"
                + "A" + number + "," + exam + ",0105,특별[일반고],UNIT,합성학과,SYNTHETIC,20270201,졸업예정,DOMESTIC_HIGH_SCHOOL,1,1,0,0\r\n");
            files.put(part(number, "raw_student_records"), COMMON + ",graduateYear,graduateGrade,specializedSchoolYN,applicantScCode\r\n"
                + common + "B" + number + ",2027,3,N,1\r\n");
            files.put(part(number, "raw_student_subjects"), subjectHeader() + "\r\n" + common
                + "C" + number + ",B" + number + ",1,1,국어,합성과목,3,2,\\N,90,80,10,100,\\N,\\N,01\r\n");
        }
        files.put("file-index.csv", index.toString());
        return files;
    }
    private static class SetHolder {
        static final java.util.Set<String> NONEMPTY = java.util.Set.of("applicants", "raw_student_records", "raw_student_subjects");
    }
}
