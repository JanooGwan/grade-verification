package com.jinhakapply.gradevalidation.transcript.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Keep source classification problems separate from missing or ambiguous record linkage. */
final class MjcArchiveInputIssues {
    private static final List<String> CODES = List.of("ACADEMIC_TYPE_UNRESOLVED", "STUDENT_RECORD_MISSING",
        "STUDENT_RECORD_MULTIPLE", "VALID_GED_RECORD_MISSING", "VALID_GED_RECORD_MULTIPLE");

    private MjcArchiveInputIssues() {}

    static String summary(List<MjcArchiveApplicant> applicants) {
        Map<String, Long> counts = applicants.stream().filter(row -> row.issue() != null)
            .collect(Collectors.groupingBy(MjcArchiveApplicant::issue, Collectors.counting()));
        if (counts.isEmpty()) return null;
        List<String> summaries = new ArrayList<>();
        for (String code : CODES) {
            Long count = counts.get(code);
            if (count != null) summaries.add(label(code) + " " + count + "건");
        }
        long other = counts.entrySet().stream().filter(entry -> !CODES.contains(entry.getKey()))
            .mapToLong(Map.Entry::getValue).sum();
        if (other > 0) summaries.add("기타 원자료 상태 확인 필요 " + other + "건");
        String result = String.join("; ", summaries) + ". 성적검증에서 사유별로 구분합니다.";
        if (counts.containsKey("ACADEMIC_TYPE_UNRESOLVED")) {
            result += " 학력 분류 코드 미인식은 원본 자료가 없다는 뜻이 아닙니다.";
        }
        return result;
    }

    private static String label(String code) {
        return switch (code) {
            case "ACADEMIC_TYPE_UNRESOLVED" -> "학력 분류 코드 인식 필요";
            case "STUDENT_RECORD_MISSING" -> "학생부 원본 미연결";
            case "STUDENT_RECORD_MULTIPLE" -> "학생부 원본 복수 연결";
            case "VALID_GED_RECORD_MISSING" -> "유효 검정고시 원본 없음";
            case "VALID_GED_RECORD_MULTIPLE" -> "유효 검정고시 원본 복수 연결";
            default -> "원자료 상태 확인 필요";
        };
    }

    static String reason(String code) {
        return switch (code) {
            case "ACADEMIC_TYPE_UNRESOLVED" -> "원자료의 학력 분류 코드를 인식하지 못했습니다. 학생부·검정고시 자료 미수신을 뜻하지 않습니다.";
            case "STUDENT_RECORD_MISSING" -> "지원자에 연결된 학생부 원본이 없습니다. 수신 대상 여부와 원본 연결을 확인해야 합니다.";
            case "STUDENT_RECORD_MULTIPLE" -> "지원자에 연결된 학생부 원본이 여러 건입니다. 임의로 선택하거나 합산하지 않았습니다.";
            case "VALID_GED_RECORD_MISSING" -> "계산에 사용할 유효한 검정고시 원본이 없습니다. 무효 원본은 계산하지 않았습니다.";
            case "VALID_GED_RECORD_MULTIPLE" -> "유효한 검정고시 원본이 여러 건입니다. 임의로 선택하거나 합산하지 않았습니다.";
            default -> "원자료 상태를 확인해야 합니다. 원본을 임의로 선택하거나 합산하지 않았습니다.";
        };
    }
}
