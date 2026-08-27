package com.jinhakapply.gradevalidation.transcript.service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jinhakapply.gradevalidation.transcript.service.SyuSourceExcelStreamer.SourceAttendanceRow;
import com.jinhakapply.gradevalidation.transcript.service.SyuSourceExcelStreamer.SourceCourseRow;
import com.jinhakapply.gradevalidation.transcript.service.SyuSourceExcelStreamer.SourceScanResult;
import com.jinhakapply.gradevalidation.transcript.service.SyuSourceExcelStreamer.StreamResult;
import com.jinhakapply.gradevalidation.transcript.service.SyuSourceExcelStreamer.WorkbookStreamResult;
import com.jinhakapply.gradevalidation.global.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
class SyuSourceImportProcessor {
    private static final int BATCH_SIZE = 5_000;
    private static final int STUDENT_QUERY_BATCH_SIZE = 5_000;
    private static final int PROGRESS_UPDATE_INTERVAL = 10_000;

    private final SyuSourceExcelStreamer streamer;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final TranscriptSnapshotReplacementService snapshotReplacementService;

    @Async("sourceImportExecutor")
    public void process(
        Long importId, Long universityId, int admissionYear, Path path, String sourceFileName
    ) {
        try {
            transactionTemplate.executeWithoutResult(status -> importWorkbook(
                importId, universityId, admissionYear, path, sourceFileName
            ));
        } catch (Exception exception) {
            log.error("SYU source workbook import failed: importId={}", importId, exception);
            deleteSnapshotsSafely(importId);
            fail(importId, safeMessage(exception));
        } finally {
            try {
                Files.deleteIfExists(path);
            } catch (Exception exception) {
                log.warn("Could not delete source import temporary file: {}", path.getFileName());
            }
        }
    }

    private void upsertStudents(Long universityId, int admissionYear, List<String> applicantNumbers) {
        String sql = """
            INSERT IGNORE INTO student (
                university_id, admission_year, applicant_number, name, education_background,
                high_school_type, graduation_status, created_at, updated_at
            ) VALUES (?, ?, ?, '미등록', 'DOMESTIC_HIGH_SCHOOL', 'GENERAL', 'EXPECTED_GRADUATE',
                CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """;
        jdbcTemplate.batchUpdate(sql, applicantNumbers, BATCH_SIZE, (statement, applicantNumber) -> {
            statement.setLong(1, universityId);
            statement.setInt(2, admissionYear);
            statement.setString(3, applicantNumber);
        });
    }

    private Map<String, Long> loadStudentIds(Long universityId, int admissionYear, List<String> applicantNumbers) {
        Map<String, Long> ids = new HashMap<>();
        for (int start = 0; start < applicantNumbers.size(); start += STUDENT_QUERY_BATCH_SIZE) {
            List<String> batch = applicantNumbers.subList(
                start, Math.min(start + STUDENT_QUERY_BATCH_SIZE, applicantNumbers.size())
            );
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            String sql = "SELECT id, applicant_number FROM student WHERE university_id = ? AND admission_year = ? "
                + "AND applicant_number IN (" + placeholders + ")";
            jdbcTemplate.query(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setLong(1, universityId);
                statement.setInt(2, admissionYear);
                for (int index = 0; index < batch.size(); index++) {
                    statement.setString(index + 3, batch.get(index));
                }
                return statement;
            }, (org.springframework.jdbc.core.RowCallbackHandler) resultSet ->
                ids.put(resultSet.getString("applicant_number"), resultSet.getLong("id")));
        }
        return ids;
    }

    private void replaceCourses(
        List<SourceCourseRow> rows, Map<String, Long> studentIds, String sourceFileName, Long importId
    ) {
        String sql = """
            INSERT INTO student_transcript_course (
                student_id, source_import_id, school_year, semester, subject_category, course_name, course_name_normalized,
                grade_value, grade_scale, achievement, raw_score, mean_score, standard_deviation,
                student_count, rank_position, tied_rank_count, legacy_achievement, credits,
                career_subject, professional_course, source_file_name, source_row_number, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'NINE_LEVEL', ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?,
                CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """;
        jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (statement, row) -> bindCourse(
            statement, row, studentIds.get(row.applicantNumber()), sourceFileName, importId
        ));
    }

    private void bindCourse(PreparedStatement statement, SourceCourseRow row, Long studentId,
        String sourceFileName, Long importId) throws java.sql.SQLException {
        statement.setLong(1, studentId);
        statement.setLong(2, importId);
        statement.setInt(3, row.schoolYear());
        statement.setInt(4, row.semester());
        statement.setString(5, row.subjectCategory().name());
        statement.setString(6, row.courseName());
        statement.setString(7, TextNormalizer.normalizeCourseName(row.courseName()));
        setInteger(statement, 8, row.grade());
        setString(statement, 9, row.achievement() == null ? null : row.achievement().name());
        setDecimal(statement, 10, row.rawScore());
        setDecimal(statement, 11, row.meanScore());
        setDecimal(statement, 12, row.standardDeviation());
        setInteger(statement, 13, row.studentCount());
        setInteger(statement, 14, row.rankPosition());
        setInteger(statement, 15, row.tiedRankCount());
        statement.setBigDecimal(16, row.credits());
        statement.setBoolean(17, row.careerSubject());
        statement.setBoolean(18, row.professionalCourse());
        statement.setString(19, sourceFileName);
        statement.setInt(20, row.rowNumber());
    }

    private String courseKey(SourceCourseRow row) {
        return row.applicantNumber() + ':' + row.schoolYear() + ':' + row.semester() + ':'
            + TextNormalizer.normalizeCourseName(row.courseName());
    }

    private void insertCourseSnapshots(List<SourceCourseRow> rows, Long importId) {
        String sql = """
            INSERT INTO student_transcript_import_course (
                import_id, source_row_number, applicant_number, school_year, semester,
                subject_category, course_name, grade_value, grade_scale, achievement,
                raw_score, mean_score, standard_deviation, student_count, rank_position,
                tied_rank_count, legacy_achievement, credits, career_subject,
                professional_course, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'NINE_LEVEL', ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?,
                CURRENT_TIMESTAMP(6))
            """;
        jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (statement, row) -> {
            statement.setLong(1, importId);
            statement.setInt(2, row.rowNumber());
            statement.setString(3, row.applicantNumber());
            statement.setInt(4, row.schoolYear());
            statement.setInt(5, row.semester());
            statement.setString(6, row.subjectCategory().name());
            statement.setString(7, row.courseName());
            setInteger(statement, 8, row.grade());
            setString(statement, 9, row.achievement() == null ? null : row.achievement().name());
            setDecimal(statement, 10, row.rawScore());
            setDecimal(statement, 11, row.meanScore());
            setDecimal(statement, 12, row.standardDeviation());
            setInteger(statement, 13, row.studentCount());
            setInteger(statement, 14, row.rankPosition());
            setInteger(statement, 15, row.tiedRankCount());
            statement.setBigDecimal(16, row.credits());
            statement.setBoolean(17, row.careerSubject());
            statement.setBoolean(18, row.professionalCourse());
        });
    }

    private void deleteSnapshots(Long importId) {
        jdbcTemplate.update("DELETE FROM student_transcript_import_course WHERE import_id=?", importId);
    }

    private void deleteSnapshotsSafely(Long importId) {
        try {
            deleteSnapshots(importId);
        } catch (Exception cleanupException) {
            log.warn("Could not remove partial source import snapshots: importId={}", importId, cleanupException);
        }
    }

    private void importWorkbook(
        Long importId, Long universityId, int admissionYear, Path path, String sourceFileName
    ) {
        long processStarted = System.nanoTime();
        long scanStarted = System.nanoTime();
        SourceScanResult scan = streamer.scan(path);
        log.info("SYU source scan completed: importId={}, courseRows={}, applicants={}, elapsedMs={}",
            importId, scan.courseRows(), scan.applicantNumbers().size(), elapsedMillis(scanStarted));
        int sourceAdmissionYear = requireSingleSourceAdmissionYear(scan.admissionYears());
        recordSourceAdmissionYear(importId, sourceAdmissionYear);
        log.info("SYU source year mapping: importId={}, sourceAdmissionYear={}, ruleAdmissionYear={}",
            importId, sourceAdmissionYear, admissionYear);

        updateStatus(importId, "PROCESSING", null);
        deleteSnapshots(importId);
        TranscriptSnapshotReplacementService.SnapshotScope snapshot = snapshotReplacementService.clear(
            universityId, admissionYear, true
        );
        List<String> applicantNumbers = scan.applicantNumbers().stream().sorted().toList();
        long studentsStarted = System.nanoTime();
        upsertStudents(universityId, admissionYear, applicantNumbers);
        Map<String, Long> studentIds = loadStudentIds(universityId, admissionYear, applicantNumbers);
        log.info("SYU source student preparation completed: importId={}, applicants={}, elapsedMs={}",
            importId, applicantNumbers.size(), elapsedMillis(studentsStarted));
        if (!studentIds.keySet().containsAll(scan.applicantNumbers())) {
            throw new IllegalStateException("일부 지원자 기본정보를 생성하지 못했습니다.");
        }

        int[] processed = {0};
        int[] nextProgressUpdate = {PROGRESS_UPDATE_INTERVAL};
        Set<String> importedCourseKeys = new HashSet<>();
        long streamStarted = System.nanoTime();
        WorkbookStreamResult streamResult = streamer.streamWorkbook(
            path,
            BATCH_SIZE,
            batch -> {
                List<SourceCourseRow> distinctRows = batch.stream()
                    .filter(row -> importedCourseKeys.add(courseKey(row)))
                    .toList();
                replaceCourses(distinctRows, studentIds, sourceFileName, importId);
                insertCourseSnapshots(distinctRows, importId);
                processed[0] += batch.size();
                if (processed[0] >= nextProgressUpdate[0]) {
                    updateProgress(importId, scan.courseRows(), processed[0], 0);
                    while (nextProgressUpdate[0] <= processed[0]) {
                        nextProgressUpdate[0] += PROGRESS_UPDATE_INTERVAL;
                    }
                }
            },
            batch -> replaceAttendance(batch, studentIds, importId)
        );
        StreamResult courses = streamResult.courses();
        StreamResult attendance = streamResult.attendance();
        int failed = courses.failedRows() + attendance.failedRows();
        log.info("SYU source data stream completed: importId={}, courseImported={}, attendanceImported={}, "
                + "failedRows={}, elapsedMs={}",
            importId, courses.importedRows(), attendance.importedRows(), failed, elapsedMillis(streamStarted));
        if (failed > 0) {
            throw new IllegalArgumentException("오류 행이 %,d건 있어 기존 저장본을 교체하지 않았습니다: %s".formatted(
                failed, summarizeErrors(courses.errors(), attendance.errors())
            ));
        }
        snapshotReplacementService.deleteMissingStudents(snapshot.existingStudents(), scan.applicantNumbers());
        int total = scan.courseRows() + attendance.importedRows();
        int imported = courses.importedRows() + attendance.importedRows();
        complete(importId, total, imported, 0, null);
        log.info("SYU source workbook import completed: importId={}, totalRows={}, importedRows={}, elapsedMs={}",
            importId, total, imported, elapsedMillis(processStarted));
    }

    private void replaceAttendance(List<SourceAttendanceRow> rows, Map<String, Long> studentIds, Long importId) {
        String sql = """
            INSERT INTO student_attendance (
                student_id, source_import_id, school_year, unexcused_absence_days, unexcused_tardy_count,
                unexcused_early_leave_count, unexcused_class_absence_count, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """;
        jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (statement, row) -> {
            Long studentId = studentIds.get(row.applicantNumber());
            if (studentId == null) throw new IllegalArgumentException("출결 지원자를 찾을 수 없습니다: " + row.applicantNumber());
            statement.setLong(1, studentId);
            statement.setLong(2, importId);
            statement.setInt(3, row.schoolYear());
            statement.setInt(4, row.absenceDays());
            statement.setInt(5, row.tardyCount());
            statement.setInt(6, row.earlyLeaveCount());
            statement.setInt(7, row.classAbsenceCount());
        });
    }

    private void updateStatus(Long importId, String status, String errorMessage) {
        jdbcTemplate.update(
            "UPDATE student_transcript_import SET status=?, error_message=?, updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
            status, errorMessage, importId
        );
    }

    private void recordSourceAdmissionYear(Long importId, int sourceAdmissionYear) {
        jdbcTemplate.update(
            "UPDATE student_transcript_import SET source_admission_year=?, updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
            sourceAdmissionYear, importId
        );
    }

    static int requireSingleSourceAdmissionYear(Set<Integer> admissionYears) {
        if (admissionYears == null || admissionYears.size() != 1) {
            throw new IllegalArgumentException(
                "삼육대 원천 파일에는 하나의 입학연도만 있어야 합니다: " + admissionYears
            );
        }
        return admissionYears.iterator().next();
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
            "UPDATE student_transcript_import SET total_rows=?, imported_rows=?, failed_rows=?, status=?, error_message=?, temporary_file_path=NULL, updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
            totalRows, importedRows, failedRows, status, warning, importId
        );
    }

    private void fail(Long importId, String message) {
        jdbcTemplate.update(
            "UPDATE student_transcript_import SET status='FAILED', error_message=?, temporary_file_path=NULL, updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
            message, importId
        );
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
