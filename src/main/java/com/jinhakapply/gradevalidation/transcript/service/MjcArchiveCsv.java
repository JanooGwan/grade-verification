package com.jinhakapply.gradevalidation.transcript.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** RFC-4180 reader. Errors deliberately contain no cell values or source paths. */
final class MjcArchiveCsv {
    private MjcArchiveCsv() {}

    static void read(Path path, Consumer<Map<String, String>> consumer) {
        read(path, List.of(), consumer);
    }

    static void read(Path path, List<String> requiredHeaders, Consumer<Map<String, String>> consumer) {
        try (var reader = new PushbackReader(Files.newBufferedReader(path, StandardCharsets.UTF_8), 1)) {
            List<String> headers = record(reader);
            if (headers == null || headers.isEmpty()) throw invalid();
            headers.set(0, headers.getFirst().replaceFirst("^\uFEFF", ""));
            if (new HashSet<>(headers).size() != headers.size() || headers.contains("")) throw invalid();
            if (!headers.containsAll(requiredHeaders)) throw invalid();
            List<String> cells;
            while ((cells = record(reader)) != null) {
                if (cells.size() != headers.size()) throw invalid();
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) row.put(headers.get(i), cells.get(i));
                consumer.accept(row);
            }
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private static List<String> record(PushbackReader reader) throws IOException {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        boolean started = false;
        int read;
        while ((read = reader.read()) != -1) {
            started = true;
            char c = (char) read;
            if (c == '\uFEFF' && cells.isEmpty() && cell.isEmpty() && !quoted) continue;
            if (quoted) {
                if (c == '"') {
                    int next = reader.read();
                    if (next == '"') cell.append('"');
                    else {
                        quoted = false;
                        closed = true;
                        if (next != -1) reader.unread(next);
                    }
                } else cell.append(c);
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
                closed = false;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r') {
                    int next = reader.read();
                    if (next != '\n' && next != -1) reader.unread(next);
                }
                cells.add(cell.toString());
                return cells;
            } else if (c == '"' && cell.isEmpty() && !closed) quoted = true;
            else if (closed || c == '"') throw invalid();
            else cell.append(c);
            if (cell.length() > 100_000 || cells.size() > 256) throw invalid();
        }
        if (quoted) throw invalid();
        if (!started) return null;
        cells.add(cell.toString());
        return cells;
    }

    static String decode(String value) {
        if (value == null || value.equals("\\N")) return null;
        if (value.startsWith("\\\\")) return value.substring(1);
        if (value.startsWith("'") && value.substring(1).stripLeading().matches("(?s)^[=+@-].*")) {
            return value.substring(1);
        }
        return value;
    }

    static void write(BufferedWriter writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) writer.write(',');
            String value = values.get(i);
            // Preserve null vs empty and escaped literal backslashes in the canonical bundle.
            if (value == null) value = "\\N";
            else if (value.startsWith("\\")) value = "\\" + value;
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }

    static IllegalArgumentException invalid() {
        return new IllegalArgumentException("CSV 구조 또는 연결 정보가 올바르지 않습니다. 원자료의 헤더와 파일 구성을 확인해 주세요.");
    }
}
