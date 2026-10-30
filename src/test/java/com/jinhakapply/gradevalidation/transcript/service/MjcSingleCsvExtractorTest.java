package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class MjcSingleCsvExtractorTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final MjcSingleCsvExtractor single = new MjcSingleCsvExtractor(mapper);
    private final MjcSourceArchiveExtractor archive = new MjcSourceArchiveExtractor();

    @Test
    void importsCombinedPartsAndSplitSubjectsWithoutLosingLinksOrLiteralValues() throws Exception {
        var fixture = MjcSourceArchiveExtractorTest.fixture(2);
        fixture.replaceAll((name, content) -> name.endsWith("-raw_student_subjects.csv")
            ? content.replace("합성과목", "\\\\N") : content);
        Path input = tagged(fixture, false);
        Path split = Files.createDirectory(directory.resolve("split"));
        var bundle = archive.extractCombined(single.extract(input, split), split);
        assertThat(bundle.applicants()).hasSize(2).allSatisfy(applicant -> {
            assertThat(applicant.part()).isZero();
            assertThat(applicant.issue()).isNull();
            assertThat(MjcArchiveScores.sourceValues(applicant)).doesNotContainKey("part");
        });
        List<MjcSourceCsvReader.CourseRow> courses = new ArrayList<>();
        new MjcSourceCsvReader().streamCourses(bundle.subjectScoreFile(), 1, courses::addAll);
        assertThat(courses).hasSize(2);
        assertThat(courses).extracting(MjcSourceCsvReader.CourseRow::courseName).containsExactly("\\N", "\\N");
        assertThat(courses).extracting(MjcSourceCsvReader.CourseRow::sourceSubjectId).containsExactly("C1", "C2");
    }

    @Test
    void rejectsChangedContinuationHeader() throws Exception {
        var fixture = MjcSourceArchiveExtractorTest.fixture(2);
        Path wrong = tagged(fixture, true);
        Path output = Files.createDirectory(directory.resolve("bad"));
        assertThatThrownBy(() -> single.extract(wrong, output)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCrossApplicantLinksBeforeSaving() throws Exception {
        var fixture = MjcSourceArchiveExtractorTest.fixture(2);
        String subject = "parts/current-part-0002-raw_student_subjects.csv";
        fixture.put(subject, fixture.get(subject).replace("A2,00000002", "A1,00000002"));
        Path input = tagged(fixture, false);
        Path split = Files.createDirectory(directory.resolve("bad-link"));
        Map<String, Path> files = single.extract(input, split);
        assertThatThrownBy(() -> archive.extractCombined(files, split))
            .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("00000002");
    }

    private Path tagged(Map<String, String> files, boolean badHeader) throws Exception {
        Path target = Files.createTempFile(directory, "tagged-", ".csv");
        List<String> types = List.of("applicants", "current_subject_overrides", "intern_input", "raw_attendance",
            "raw_evaluation_scores", "raw_ged_records", "raw_ged_subjects", "raw_student_records", "raw_student_subjects");
        try (BufferedWriter writer = Files.newBufferedWriter(target)) {
            raw(writer, List.of("sheet", "row_kind", "values_json"));
            for (String type : types) {
                if (type.equals("intern_input")) {
                    taggedRow(writer, type, "header", List.of("id"));
                    continue;
                }
                for (int part = 1; part <= 2; part++) {
                    Path partFile = Files.createTempFile(directory, "source-", ".csv");
                    Files.writeString(partFile, files.get("parts/current-part-%04d-%s.csv".formatted(part, type)));
                    List<Map<String, String>> rows = new ArrayList<>();
                    MjcArchiveCsv.read(partFile, rows::add);
                    String headerLine = Files.readAllLines(partFile).getFirst();
                    List<String> header = new ArrayList<>(List.of(headerLine.split(",", -1)));
                    String sheet = type.equals("raw_student_subjects") && part == 2 ? type + "_2" : type;
                    if (part == 1 || sheet.endsWith("_2")) {
                        if (badHeader && sheet.endsWith("_2")) header.set(0, "bad");
                        taggedRow(writer, sheet, "header", header);
                    }
                    for (Map<String, String> row : rows) taggedRow(writer, sheet, "data", new ArrayList<>(row.values()));
                }
            }
        }
        return target;
    }

    private void taggedRow(BufferedWriter writer, String sheet, String kind, List<String> values) throws Exception {
        raw(writer, List.of(sheet, kind, mapper.writeValueAsString(values)));
    }

    private void raw(BufferedWriter writer, List<String> values) throws Exception {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) writer.write(',');
            writer.write('"'); writer.write(values.get(i).replace("\"", "\"\"")); writer.write('"');
        }
        writer.newLine();
    }
}
