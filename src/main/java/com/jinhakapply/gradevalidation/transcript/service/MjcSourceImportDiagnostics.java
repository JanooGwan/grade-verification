package com.jinhakapply.gradevalidation.transcript.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Import diagnostics contain structure and error codes only, never CSV values or SQL text. */
final class MjcSourceImportDiagnostics {
    enum Stage {
        ARCHIVE_VALIDATION("ZIP 구성·원자료 연결 검사"),
        SINGLE_CSV_VALIDATION("통합 CSV 구성·원자료 연결 검사"),
        LOCK_SCOPE("저장 범위 잠금"),
        VALIDATE_CSV("계산 입력 변환 검사"),
        REPLACE_SNAPSHOT("이전 저장본 교체 준비"),
        SAVE_CATALOG("전형·모집단위 저장"),
        SAVE_STUDENTS("지원자 기본정보 저장"),
        SAVE_APPLICATIONS("지원정보 저장"),
        SAVE_METADATA("원자료 연결정보 저장"),
        READ_COURSES("과목 입력 검사"),
        SAVE_COURSES("과목 저장"),
        SAVE_COURSE_SNAPSHOTS("원본 과목 저장본 생성"),
        FINALIZE("저장 완료 처리");

        final String label;
        Stage(String label) { this.label = label; }
    }

    private static final String TYPES = "applicants|raw_student_records|raw_student_subjects|raw_ged_records|raw_ged_subjects|raw_attendance|current_subject_overrides|raw_evaluation_scores";
    private static final Pattern SOURCE = Pattern.compile("^MJC_CSV_(INPUT|ROWS|COUNTS):part=([0-9]{1,4});file=(" + TYPES + ");row=([0-9]{1,7})$");
    private static final Pattern CANONICAL = Pattern.compile("^(canonical-(?:applicants|bases|courses)\\.csv) ([0-9]{1,9})행(?: \\[part=([0-9]{1,4})\\])?: (.*)$");
    private static final Pattern MISSING_FILE = Pattern.compile("^MJC_ZIP_MISSING_FILE:(execution\\.csv|file-index\\.csv|parts/current-part-[0-9]{4}-(?:" + TYPES + ")\\.csv)$");
    private static final Set<String> COLUMNS = Set.of("examNumber", "admissionTypeCode", "admissionTypeName",
        "recruitmentUnitCode", "recruitmentUnitName", "graduateYear", "graduateGrade", "unit", "grade", "term",
        "subjectName", "rankingGrade", "originalScore", "avgScore", "standardDeviation", "studentCount", "rank", "sameRank");
    private static final List<String> INPUT_REASONS = List.of("값이 없습니다.", "값이 숫자가 아닙니다.", "값이 정수가 아닙니다.",
        "값은 1~3 사이여야 합니다.", "값은 1~2 사이여야 합니다.", "값이 석차등급 또는 인식 가능한 비등급 값이 아닙니다.");

    private MjcSourceImportDiagnostics() {}

    static IllegalArgumentException source(int part, String fileType, int row, boolean rowCount, Throwable cause) {
        // fileType comes from the extractor's fixed allowlist, not archive member names.
        String detail = "MJC_CSV_" + (rowCount ? "ROWS" : "INPUT") + ":part=" + part + ";file=" + fileType + ";row=" + row;
        return new IllegalArgumentException(detail, cause);
    }

    static IllegalArgumentException countMismatch(int part, String fileType, int count) {
        return new IllegalArgumentException("MJC_CSV_COUNTS:part=" + part + ";file=" + fileType + ";row=" + count);
    }

    static String describe(Exception exception, Stage stage) {
        StringBuilder result = new StringBuilder("[MJC_IMPORT_FAILED] 단계=").append(stage.name())
            .append("(").append(stage.label).append("); 유형=").append(exception.getClass().getSimpleName());
        Throwable cursor = exception;
        for (int depth = 0; cursor != null && depth < 12; depth++, cursor = cursor.getCause()) {
            if (cursor instanceof SQLException sql) {
                for (int next = 0; next < 5 && sql.getNextException() != null; next++) sql = sql.getNextException();
                String state = sql.getSQLState();
                result.append("; SQLState=").append(state != null && state.matches("[A-Z0-9]{5}") ? state : "미제공")
                    .append("; DB코드=").append(sql.getErrorCode());
                break;
            }
            String message = cursor instanceof IllegalArgumentException ? cursor.getMessage() : null;
            if (message == null) continue;
            var source = SOURCE.matcher(message);
            if (source.matches()) {
                result.append("; part=").append(source.group(2)).append("; 파일종류=").append(source.group(3))
                    .append("; 데이터행=").append(source.group(4))
                    .append("; 검사=").append(switch (source.group(1)) {
                        case "ROWS" -> "인덱스 행 수 불일치";
                        case "COUNTS" -> "지원자 명세의 원자료 건수 불일치";
                        default -> "원자료 형식·연결 검사";
                    });
            }
            var canonical = CANONICAL.matcher(message);
            if (canonical.matches()) {
                result.append("; 변환파일=").append(canonical.group(1)).append("; 행=").append(canonical.group(2));
                if (canonical.group(3) != null) result.append("; part=").append(canonical.group(3));
                String reason = canonical.group(4);
                for (String column : COLUMNS) {
                    if (INPUT_REASONS.stream().anyMatch(suffix -> reason.equals(column + " " + suffix))) {
                        result.append("; 컬럼=").append(column).append("; 검사=").append(reason.substring(column.length() + 1));
                        break;
                    }
                }
                if (reason.equals("석차등급은 1~9 사이여야 합니다.")) result.append("; 컬럼=rankingGrade; 검사=등급 범위");
                if (reason.equals("이수단위는 음수일 수 없습니다.")) result.append("; 컬럼=unit; 검사=음수 단위");
            }
            var missing = MISSING_FILE.matcher(message);
            if (missing.matches()) result.append("; 누락파일=").append(missing.group(1));
            if (message.equals("명지전문대 현재 수신 원자료 ZIP과 선택한 학년도가 일치해야 합니다.")) {
                result.append("; 검사=execution.csv의 대학·학년도·원자료 기준 불일치");
            }
            if (message.equals("ZIP을 읽지 못했습니다. 암호화 ZIP은 먼저 해제한 뒤 폴더를 일반 ZIP으로 다시 압축해 주세요.")) {
                result.append("; 검사=ZIP 형식·압축 방식·암호화 여부 확인 필요");
            }
            if (message.equals("지원자 ID 또는 수험번호가 중복되어 자동 연결할 수 없습니다. 원자료 구성을 확인해 주세요.")) {
                result.append("; 검사=지원자 ID·수험번호 중복(값은 표시하지 않음)");
            }
        }
        result.append(". 기존 저장본은 유지됩니다. 원자료 값 대신 이 오류 문구만 확인해 주세요.");
        return result.toString();
    }
}
