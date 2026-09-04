package com.jinhakapply.gradevalidation.transcript.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import org.springframework.stereotype.Component;

@Component
class MjcSourceCsvReader {

    static final String SOURCE_FORMAT = "MJC_SOURCE_CSV_BUNDLE_V1";
    private static final Set<String> NON_NUMERIC_RANKING_GRADES = Set.of(
        "P", "·", "이수", "우수", "보통", "미흡"
    );

    List<ApplicantRow> readApplicants(Path path) {
        List<ApplicantRow> rows = new ArrayList<>();
        read(path, row -> rows.add(new ApplicantRow(
            row.required("examNumber"), row.required("admissionTypeCode"), row.required("admissionTypeName"),
            row.required("recruitmentUnitCode"), row.required("recruitmentUnitName"),
            row.optional("highSchoolCode"), row.optional("graduationDate"), row.optional("graduationStatus")
        )));
        return List.copyOf(rows);
    }

    Map<String, BaseInfoRow> readBaseInfo(Path path) {
        Map<String, BaseInfoRow> rows = new LinkedHashMap<>();
        read(path, row -> {
            BaseInfoRow value = new BaseInfoRow(
                row.required("examNumber"), row.optionalInteger("graduateYear"), row.optionalInteger("graduateGrade"),
                row.optional("specializedSchoolYN"), row.optional("applicantScCode")
            );
            if (rows.putIfAbsent(value.examNumber(), value) != null) {
                throw row.error("학생부 기본정보에 중복 수험번호가 있습니다.");
            }
        });
        return Map.copyOf(rows);
    }

    StreamResult streamCourses(Path path, int batchSize, Consumer<List<CourseRow>> batchConsumer) {
        List<CourseRow> batch = new ArrayList<>(batchSize);
        int[] total = {0};
        int[] skipped = {0};
        read(path, row -> {
            total[0]++;
            BigDecimal credits = row.optionalDecimal("unit");
            if (credits == null || credits.signum() <= 0) {
                skipped[0]++;
                return;
            }
            Integer grade = row.optionalRankingGrade("rankingGrade");
            if (grade != null && (grade < 1 || grade > 9)) {
                throw row.error("석차등급은 1~9 사이여야 합니다.");
            }
            String organizationName = row.optional("organizationName");
            String separationCode = row.optional("subjectSeparationCode");
            CourseRow value = new CourseRow(
                row.rowNumber(), row.required("examNumber"), row.requiredInteger("grade", 1, 3),
                row.requiredInteger("term", 1, 2), subjectCategory(organizationName),
                row.required("subjectName"), grade, achievement(row.optional("achievement")),
                row.optionalDecimal("originalScore"), row.optionalDecimal("avgScore"),
                row.optionalDecimal("standardDeviation"), row.optionalInteger("studentCount"),
                row.optionalInteger("rank"), row.optionalInteger("sameRank"), credits,
                "02".equals(separationCode) || "2".equals(separationCode),
                isProfessional(organizationName)
            );
            batch.add(value);
            if (batch.size() >= batchSize) {
                batchConsumer.accept(List.copyOf(batch));
                batch.clear();
            }
        });
        if (!batch.isEmpty()) batchConsumer.accept(List.copyOf(batch));
        return new StreamResult(total[0], total[0] - skipped[0], skipped[0]);
    }

    private void read(Path path, Consumer<CsvRow> consumer) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) throw new IllegalArgumentException(path.getFileName() + " 헤더가 없습니다.");
            List<String> headers = parseLine(stripBom(headerLine));
            Map<String, Integer> columns = new HashMap<>();
            for (int index = 0; index < headers.size(); index++) columns.put(headers.get(index).trim(), index);
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) continue;
                List<String> values = parseLine(line);
                if (values.size() != headers.size()) {
                    throw new IllegalArgumentException(path.getFileName() + " " + rowNumber
                        + "행의 열 수가 헤더와 다릅니다.");
                }
                consumer.accept(new CsvRow(path.getFileName().toString(), rowNumber, columns, values));
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException(path.getFileName() + " CSV를 읽지 못했습니다.", exception);
        }
    }

    static List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        if (quoted) throw new IllegalArgumentException("닫히지 않은 CSV 따옴표가 있습니다.");
        values.add(value.toString());
        return values;
    }

    private String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private AchievementLevel achievement(String value) {
        if (value == null) return null;
        try {
            return AchievementLevel.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private SubjectCategory subjectCategory(String organizationName) {
        String value = organizationName == null ? "" : organizationName.replaceAll("\\s", "");
        if (isProfessional(value) || isOtherOrganization(value)) return SubjectCategory.OTHER;
        if (value.contains("국어")) return SubjectCategory.KOREAN;
        if (value.contains("수학")) return SubjectCategory.MATH;
        if (value.contains("영어")) return SubjectCategory.ENGLISH;
        if (value.contains("과학")) return SubjectCategory.SCIENCE;
        if (value.contains("사회") || value.contains("역사") || value.contains("도덕") || value.contains("한국사")) {
            return SubjectCategory.SOCIAL;
        }
        return SubjectCategory.OTHER;
    }

    private boolean isOtherOrganization(String value) {
        return value.contains("기술") || value.contains("가정") || value.contains("제2외국어")
            || value.contains("한문") || value.contains("교양") || value.contains("예술")
            || value.contains("음악") || value.contains("미술") || value.contains("체육");
    }

    private boolean isProfessional(String organizationName) {
        String value = organizationName == null ? "" : organizationName.replaceAll("\\s", "");
        if (value.isBlank() || value.contains("에관한교과")) return true;
        return !(value.contains("국어") || value.contains("수학") || value.contains("영어")
            || value.contains("한국사") || value.contains("사회") || value.contains("역사")
            || value.contains("도덕") || value.contains("과학") || value.contains("체육")
            || value.contains("예술") || value.contains("음악") || value.contains("미술")
            || value.contains("기술") || value.contains("가정") || value.contains("제2외국어")
            || value.contains("한문") || value.contains("교양") || value.equals("정보")
            || value.contains("보통교과"));
    }

    record ApplicantRow(
        String examNumber,
        String admissionTypeCode,
        String admissionTypeName,
        String recruitmentUnitCode,
        String recruitmentUnitName,
        String highSchoolCode,
        String graduationDate,
        String graduationStatus
    ) {}

    record BaseInfoRow(
        String examNumber,
        Integer graduateYear,
        Integer graduateGrade,
        String specializedSchoolYn,
        String applicantSchoolCode
    ) {}

    record CourseRow(
        int rowNumber,
        String examNumber,
        int schoolYear,
        int semester,
        SubjectCategory subjectCategory,
        String courseName,
        Integer grade,
        AchievementLevel achievement,
        BigDecimal rawScore,
        BigDecimal meanScore,
        BigDecimal standardDeviation,
        Integer studentCount,
        Integer rankPosition,
        Integer tiedRankCount,
        BigDecimal credits,
        boolean careerSubject,
        boolean professionalCourse
    ) {}

    record StreamResult(int totalRows, int importedRows, int skippedRows) {}

    private record CsvRow(
        String fileName,
        int rowNumber,
        Map<String, Integer> columns,
        List<String> values
    ) {
        String required(String name) {
            String value = optional(name);
            if (value == null) throw error(name + " 값이 없습니다.");
            return value;
        }

        String optional(String name) {
            Integer index = columns.get(name);
            if (index == null) throw error("필수 헤더가 없습니다: " + name);
            String value = values.get(index).trim();
            return value.isBlank() || value.equals("\\N") ? null : value;
        }

        int requiredInteger(String name, int minimum, int maximum) {
            Integer value = optionalInteger(name);
            if (value == null || value < minimum || value > maximum) {
                throw error(name + " 값은 " + minimum + "~" + maximum + " 사이여야 합니다.");
            }
            return value;
        }

        Integer optionalInteger(String name) {
            BigDecimal value = optionalDecimal(name);
            if (value == null) return null;
            try {
                return value.intValueExact();
            } catch (ArithmeticException exception) {
                throw error(name + " 값이 정수가 아닙니다.");
            }
        }

        Integer optionalRankingGrade(String name) {
            String value = optional(name);
            if (value == null) return null;
            if (NON_NUMERIC_RANKING_GRADES.contains(value.toUpperCase(Locale.ROOT))) return null;
            try {
                return new BigDecimal(value.replace(",", "")).intValueExact();
            } catch (ArithmeticException | NumberFormatException exception) {
                throw error(name + " 값이 석차등급 또는 인식 가능한 비등급 값이 아닙니다.");
            }
        }

        BigDecimal optionalDecimal(String name) {
            String value = optional(name);
            if (value == null) return null;
            try {
                return new BigDecimal(value.replace(",", ""));
            } catch (NumberFormatException exception) {
                throw error(name + " 값이 숫자가 아닙니다.");
            }
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(fileName + " " + rowNumber + "행: " + message);
        }
    }
}
