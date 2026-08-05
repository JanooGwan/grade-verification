package com.jinhakapply.gradevalidation.transcript.service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.transcript.service.SyuSourceExcelStreamer.SourceAttendanceRow;
import com.jinhakapply.gradevalidation.transcript.service.SyuSourceExcelStreamer.SourceCourseRow;
import com.jinhakapply.gradevalidation.transcript.service.SyuSourceExcelStreamer.SourceScanResult;
import com.jinhakapply.gradevalidation.transcript.service.SyuSourceExcelStreamer.StreamResult;
import com.jinhakapply.gradevalidation.transcript.service.SyuSourceExcelStreamer.WorkbookStreamResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
class SyuSourceImportProcessor {
    private static final int BATCH_SIZE = 5_000;
    private static final int STUDENT_QUERY_BATCH_SIZE = 5_000;
    private static final int PROGRESS_UPDATE_INTERVAL = 10_000;

    private final SyuSourceExcelStreamer streamer;
    private final JdbcTemplate jdbcTemplate;

    @Async("sourceImportExecutor")
    public void process(Long importId, int admissionYear, Path path, String sourceFileName) {
        long processStarted = System.nanoTime();
        try {
            updateStatus(importId, "PROCESSING", null);
            long scanStarted = System.nanoTime();
            SourceScanResult scan = streamer.scan(path);
            log.info("SYU source scan completed: importId={}, courseRows={}, applicants={}, elapsedMs={}",
                importId, scan.courseRows(), scan.applicantNumbers().size(), elapsedMillis(scanStarted));
            if (!scan.admissionYears().equals(java.util.Set.of(admissionYear))) {
                throw new IllegalArgumentException(
                    "화면의 모집연도 %d와 파일의 입학연도 %s가 일치하지 않습니다."
                        .formatted(admissionYear, scan.admissionYears())
                );
            }
            List<String> applicantNumbers = scan.applicantNumbers().stream().sorted().toList();
            long studentsStarted = System.nanoTime();
            upsertStudents(admissionYear, applicantNumbers);
            Map<String, Long> studentIds = loadStudentIds(admissionYear, applicantNumbers);
            log.info("SYU source student preparation completed: importId={}, applicants={}, elapsedMs={}",
                importId, applicantNumbers.size(), elapsedMillis(studentsStarted));
            if (!studentIds.keySet().containsAll(scan.applicantNumbers())) {
                throw new IllegalStateException("일부 지원자 기본정보를 생성하지 못했습니다.");
            }

            int[] processed = {0};
            int[] nextProgressUpdate = {PROGRESS_UPDATE_INTERVAL};
            long streamStarted = System.nanoTime();
            WorkbookStreamResult streamResult = streamer.streamWorkbook(
                path,
                BATCH_SIZE,
                batch -> {
                    upsertCourses(batch, studentIds, sourceFileName);
                    processed[0] += batch.size();
                    if (processed[0] >= nextProgressUpdate[0]) {
                        updateProgress(importId, scan.courseRows(), processed[0], 0);
                        while (nextProgressUpdate[0] <= processed[0]) {
                            nextProgressUpdate[0] += PROGRESS_UPDATE_INTERVAL;
                        }
                    }
                },
                batch -> upsertAttendance(batch, studentIds)
            );
            StreamResult courses = streamResult.courses();
            StreamResult attendance = streamResult.attendance();
            log.info("SYU source data stream completed: importId={}, courseImported={}, attendanceImported={}, "
                    + "failedRows={}, elapsedMs={}",
                importId, courses.importedRows(), attendance.importedRows(),
                courses.failedRows() + attendance.failedRows(), elapsedMillis(streamStarted));
            int failed = courses.failedRows() + attendance.failedRows();
            int total = scan.courseRows() + attendance.importedRows() + attendance.failedRows();
            int imported = courses.importedRows() + attendance.importedRows();
            String warning = failed == 0 ? null : summarizeErrors(courses.errors(), attendance.errors());
            complete(importId, total, imported, failed, warning);
            log.info("SYU source workbook import completed: importId={}, totalRows={}, importedRows={}, "
                    + "failedRows={}, elapsedMs={}",
                importId, total, imported, failed, elapsedMillis(processStarted));
        } catch (Exception exception) {
            log.error("SYU source workbook import failed: importId={}", importId, exception);
            fail(importId, safeMessage(exception));
        } finally {
            try {
                Files.deleteIfExists(path);
            } catch (Exception exception) {
                log.warn("Could not delete source import temporary file: {}", path.getFileName());
            }
        }
    }

    private void upsertStudents(int admissionYear, List<String> applicantNumbers) {
        String sql = """
            INSERT INTO student (
                admission_year, applicant_number, name, education_background,
                high_school_type, graduation_status, created_at, updated_at
            ) VALUES (?, ?, '미등록', 'DOMESTIC_HIGH_SCHOOL', 'GENERAL', 'EXPECTED_GRADUATE',
                CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE updated_at=updated_at
            """;
        jdbcTemplate.batchUpdate(sql, applicantNumbers, BATCH_SIZE, (statement, applicantNumber) -> {
            statement.setInt(1, admissionYear);
            statement.setString(2, applicantNumber);
        });
    }

    private Map<String, Long> loadStudentIds(int admissionYear, List<String> applicantNumbers) {
        Map<String, Long> ids = new HashMap<>();
        for (int start = 0; start < applicantNumbers.size(); start += STUDENT_QUERY_BATCH_SIZE) {
            List<String> batch = applicantNumbers.subList(
                start, Math.min(start + STUDENT_QUERY_BATCH_SIZE, applicantNumbers.size())
            );
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            String sql = "SELECT id, applicant_number FROM student WHERE admission_year = ? "
                + "AND applicant_number IN (" + placeholders + ")";
            jdbcTemplate.query(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setInt(1, admissionYear);
                for (int index = 0; index < batch.size(); index++) {
                    statement.setString(index + 2, batch.get(index));
                }
                return statement;
            }, (org.springframework.jdbc.core.RowCallbackHandler) resultSet ->
                ids.put(resultSet.getString("applicant_number"), resultSet.getLong("id")));
        }
        return ids;
    }

    private void upsertCourses(List<SourceCourseRow> rows, Map<String, Long> studentIds, String sourceFileName) {
        String sql = """
            INSERT INTO student_transcript_course (
                student_id, school_year, semester, subject_category, course_name,
                grade_value, grade_scale, achievement, raw_score, mean_score, standard_deviation,
                student_count, rank_position, tied_rank_count, legacy_achievement, credits,
                career_subject, professional_course, source_file_name, source_row_number, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, 'NINE_LEVEL', ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?,
                CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE
                grade_value=VALUES(grade_value), grade_scale=VALUES(grade_scale), achievement=VALUES(achievement),
                raw_score=VALUES(raw_score), mean_score=VALUES(mean_score), standard_deviation=VALUES(standard_deviation),
                student_count=VALUES(student_count), rank_position=VALUES(rank_position),
                tied_rank_count=VALUES(tied_rank_count), credits=VALUES(credits),
                career_subject=VALUES(career_subject), professional_course=VALUES(professional_course),
                source_file_name=VALUES(source_file_name), source_row_number=VALUES(source_row_number),
                updated_at=CURRENT_TIMESTAMP(6)
            """;
        jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (statement, row) -> bindCourse(
            statement, row, studentIds.get(row.applicantNumber()), sourceFileName
        ));
    }

    private void bindCourse(PreparedStatement statement, SourceCourseRow row, Long studentId,
        String sourceFileName) throws java.sql.SQLException {
        statement.setLong(1, studentId);
        statement.setInt(2, row.schoolYear());
        statement.setInt(3, row.semester());
        statement.setString(4, row.subjectCategory().name());
        statement.setString(5, row.courseName());
        setInteger(statement, 6, row.grade());
        setString(statement, 7, row.achievement() == null ? null : row.achievement().name());
        setDecimal(statement, 8, row.rawScore());
        setDecimal(statement, 9, row.meanScore());
        setDecimal(statement, 10, row.standardDeviation());
        setInteger(statement, 11, row.studentCount());
        setInteger(statement, 12, row.rankPosition());
        setInteger(statement, 13, row.tiedRankCount());
        statement.setBigDecimal(14, row.credits());
        statement.setBoolean(15, row.careerSubject());
        statement.setBoolean(16, row.professionalCourse());
        statement.setString(17, sourceFileName);
        statement.setInt(18, row.rowNumber());
    }

    private void upsertAttendance(List<SourceAttendanceRow> rows, Map<String, Long> studentIds) {
        String sql = """
            INSERT INTO student_attendance (
                student_id, school_year, unexcused_absence_days, unexcused_tardy_count,
                unexcused_early_leave_count, unexcused_class_absence_count, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE
                unexcused_absence_days=VALUES(unexcused_absence_days),
                unexcused_tardy_count=VALUES(unexcused_tardy_count),
                unexcused_early_leave_count=VALUES(unexcused_early_leave_count),
                unexcused_class_absence_count=VALUES(unexcused_class_absence_count),
                updated_at=CURRENT_TIMESTAMP(6)
            """;
        jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (statement, row) -> {
            Long studentId = studentIds.get(row.applicantNumber());
            if (studentId == null) throw new IllegalArgumentException("출결 지원자를 찾을 수 없습니다: " + row.applicantNumber());
            statement.setLong(1, studentId);
            statement.setInt(2, row.schoolYear());
            statement.setInt(3, row.absenceDays());
            statement.setInt(4, row.tardyCount());
            statement.setInt(5, row.earlyLeaveCount());
            statement.setInt(6, row.classAbsenceCount());
        });
    }

    private void updateStatus(Long importId, String status, String errorMessage) {
        jdbcTemplate.update(
            "UPDATE student_transcript_import SET status=?, error_message=?, updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
            status, errorMessage, importId
        );
    }

    private void updateProgress(Long importId, int totalRows, int importedRows, int failedRows) {
        jdbcTemplate.update(
            "UPDATE student_transcript_import SET total_rows=?, imported_rows=?, failed_rows=?, updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
            totalRows, importedRows, failedRows, importId
        );
    }

    private void complete(Long importId, int totalRows, int importedRows, int failedRows, String warning) {
        String status = failedRows == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS";
        jdbcTemplate.update(
            "UPDATE student_transcript_import SET total_rows=?, imported_rows=?, failed_rows=?, status=?, error_message=?, updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
            totalRows, importedRows, failedRows, status, warning, importId
        );
    }

    private void fail(Long importId, String message) {
        updateStatus(importId, "FAILED", message);
    }

    private String summarizeErrors(List<String> courseErrors, List<String> attendanceErrors) {
        return java.util.stream.Stream.concat(courseErrors.stream(), attendanceErrors.stream())
            .limit(5).collect(java.util.stream.Collectors.joining(" / "));
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = "원천 파일 처리 중 오류가 발생했습니다.";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private void setInteger(PreparedStatement statement, int index, Integer value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, Types.INTEGER); else statement.setInt(index, value);
    }

    private void setDecimal(PreparedStatement statement, int index, BigDecimal value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, Types.DECIMAL); else statement.setBigDecimal(index, value);
    }

    private void setString(PreparedStatement statement, int index, String value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR); else statement.setString(index, value);
    }
}
