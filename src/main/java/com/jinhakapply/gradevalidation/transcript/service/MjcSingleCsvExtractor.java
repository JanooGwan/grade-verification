package com.jinhakapply.gradevalidation.transcript.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Streams the tagged single CSV produced by the MJC converter; never logs cell values. */
@Component
@RequiredArgsConstructor
class MjcSingleCsvExtractor {
    private static final Set<String> TYPES = Set.of("applicants", "current_subject_overrides", "intern_input",
        "raw_attendance", "raw_evaluation_scores", "raw_ged_records", "raw_ged_subjects",
        "raw_student_records", "raw_student_subjects");
    private static final Pattern SUBJECT_PART = Pattern.compile("raw_student_subjects_(?:[2-9]|[1-9][0-9]+)");
    private final ObjectMapper mapper;

    Map<String, Path> extract(Path input, Path directory) {
        Map<String, Path> paths = new LinkedHashMap<>();
        Map<String, BufferedWriter> writers = new HashMap<>();
        Set<String> seen = new HashSet<>();
        Map<String, List<String>> headers = new HashMap<>();
        String[] activeSheet = {null};
        try {
            for (String type : TYPES) {
                if (type.equals("intern_input")) continue;
                Path target = directory.resolve("combined-" + type + ".csv");
                paths.put(type, target);
                writers.put(type, Files.newBufferedWriter(target, StandardCharsets.UTF_8));
            }
            MjcArchiveCsv.read(input, List.of("sheet", "row_kind", "values_json"), row -> {
                if (row.size() != 3) throw MjcArchiveCsv.invalid();
                String sheet = row.get("sheet");
                String kind = row.get("row_kind");
                boolean continuation = sheet != null && SUBJECT_PART.matcher(sheet).matches();
                String type = continuation ? "raw_student_subjects" : sheet;
                if (!TYPES.contains(type)) throw MjcArchiveCsv.invalid();
                List<String> values = parseValues(row.get("values_json"));
                try {
                    if ("header".equals(kind)) {
                        if (!seen.add(sheet) || values.isEmpty() || new HashSet<>(values).size() != values.size()
                            || values.contains("")) throw MjcArchiveCsv.invalid();
                        if (continuation) {
                            if (!values.equals(headers.get(type))) throw MjcArchiveCsv.invalid();
                        } else {
                            headers.put(type, values);
                            if (writers.containsKey(type)) writeRaw(writers.get(type), values);
                        }
                        activeSheet[0] = sheet;
                    } else if ("data".equals(kind)) {
                        if (!sheet.equals(activeSheet[0]) || !headers.containsKey(type)
                            || values.size() != headers.get(type).size()) throw MjcArchiveCsv.invalid();
                        if (writers.containsKey(type)) writeRaw(writers.get(type), values);
                    } else throw MjcArchiveCsv.invalid();
                } catch (IOException exception) { throw MjcArchiveCsv.invalid(); }
            }, 8 * 1024 * 1024);
            if (!seen.containsAll(TYPES)) throw MjcArchiveCsv.invalid();
            for (BufferedWriter writer : writers.values()) writer.flush();
            return paths;
        } catch (IOException exception) {
            throw MjcArchiveCsv.invalid();
        } finally {
            writers.values().forEach(writer -> { try { writer.close(); } catch (IOException ignored) { } });
        }
    }

    private List<String> parseValues(String json) {
        try {
            List<?> values = mapper.readValue(json, List.class);
            if (values == null || values.size() > 256) throw MjcArchiveCsv.invalid();
            List<String> result = new ArrayList<>(values.size());
            for (Object value : values) {
                if (!(value instanceof String text) || text.length() > 100_000) throw MjcArchiveCsv.invalid();
                result.add(text);
            }
            return result;
        } catch (Exception exception) { throw MjcArchiveCsv.invalid(); }
    }

    private void writeRaw(BufferedWriter writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) writer.write(',');
            writer.write('"');
            writer.write(values.get(i).replace("\"", "\"\""));
            writer.write('"');
        }
        writer.newLine();
    }
}
