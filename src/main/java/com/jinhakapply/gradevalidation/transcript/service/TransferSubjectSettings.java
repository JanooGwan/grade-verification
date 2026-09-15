package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.global.exception.CustomException;

/** Explicit subject assignments in the Korean transfer workbook, independent of sheet order. */
final class TransferSubjectSettings {
    static final String SHEET_NAME = "반영교과설정";
    private static final List<String> HEADERS = List.of(
        "입학연도", "모집시기", "편제코드", "편제명", "교과코드",
        "교과명", "과목코드", "과목명", "과목구분코드", "과목구분"
    );
    private final Map<Key, SubjectCategory> categories = new HashMap<>();
    private boolean headerRead;

    void readRow(int rowNumber, Map<Integer, String> values) {
        if (rowNumber == 0) {
            for (int column = 0; column < HEADERS.size(); column++) {
                if (!HEADERS.get(column).equals(text(values.get(column)))) {
                    throw invalid("열 제목이 올바르지 않습니다: " + HEADERS.get(column));
                }
            }
            headerRead = true;
            return;
        }
        if (values.values().stream().allMatch(value -> text(value).isEmpty())) return;
        requireHeader();
        for (int column : List.of(0, 1, 2, 6)) {
            if (text(values.get(column)).isEmpty()) {
                throw invalid((rowNumber + 1) + "행의 " + HEADERS.get(column) + " 값이 없습니다.");
            }
        }
        SubjectCategory category = category(values.get(8), values.get(9), rowNumber + 1);
        Key key = key(values, 2, 4, 6);
        SubjectCategory previous = categories.putIfAbsent(key, category);
        if (previous != null && previous != category) {
            throw invalid((rowNumber + 1) + "행에 동일 코드 조합의 서로 다른 반영 교과가 있습니다.");
        }
    }

    void requireHeader() {
        if (!headerRead) throw invalid("열 제목이 없습니다.");
    }

    SubjectCategory resolve(Key key) {
        // Unassigned/missing keys remain available to all-subject rules as OTHER.
        return categories.getOrDefault(key, SubjectCategory.OTHER);
    }

    static Key courseKey(Map<Integer, String> values) {
        return key(values, 5, 7, 9);
    }

    private static Key key(Map<Integer, String> values, int organization, int subject, int course) {
        return new Key(code(values.get(0)), code(values.get(1)), code(values.get(organization)),
            code(values.get(subject)), code(values.get(course)));
    }

    private static SubjectCategory category(String rawCode, String rawLabel, int row) {
        String code = code(rawCode);
        String label = text(rawLabel);
        if (code.isEmpty() && label.isEmpty()) return SubjectCategory.OTHER;
        SubjectCategory category = switch (code) {
            case "10" -> SubjectCategory.KOREAN;
            case "20" -> SubjectCategory.ENGLISH;
            case "30" -> SubjectCategory.MATH;
            case "40" -> SubjectCategory.SOCIAL;
            case "50" -> SubjectCategory.SCIENCE;
            default -> throw invalid(row + "행의 과목구분코드가 올바르지 않습니다.");
        };
        String expectedLabel = switch (category) {
            case KOREAN -> "국어";
            case ENGLISH -> "영어";
            case MATH -> "수학";
            case SOCIAL -> "사회";
            case SCIENCE -> "과학";
            default -> throw new IllegalStateException();
        };
        if (!label.isEmpty() && !label.equals(expectedLabel)) {
            throw invalid(row + "행의 과목구분코드와 과목구분이 일치하지 않습니다.");
        }
        return category;
    }

    private static String code(String value) {
        String normalized = text(value);
        if (normalized.matches("[0-9]+(?:\\.0+)?")) {
            return new BigDecimal(normalized).toBigIntegerExact().toString();
        }
        return normalized;
    }

    private static String text(String value) {
        return value == null || "NULL".equalsIgnoreCase(value.trim()) ? "" : value.trim();
    }

    private static CustomException invalid(String message) {
        return CustomException.of(INVALID_TRANSCRIPT_FILE, SHEET_NAME + ": " + message);
    }

    record Key(String admissionYear, String period, String organization, String subject, String course) {}
}
