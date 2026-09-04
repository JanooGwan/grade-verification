package com.jinhakapply.gradevalidation.transcript.service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jinhakapply.gradevalidation.global.util.TextNormalizer;
import com.jinhakapply.gradevalidation.transcript.service.MjcSourceCsvReader.ApplicantRow;
import com.jinhakapply.gradevalidation.transcript.service.MjcSourceCsvReader.BaseInfoRow;
import com.jinhakapply.gradevalidation.transcript.service.MjcSourceCsvReader.CourseRow;
import com.jinhakapply.gradevalidation.transcript.service.MjcSourceCsvReader.StreamResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
class MjcSourceImportProcessor {
    private static final int BATCH_SIZE = 5_000;
    private static final int STUDENT_QUERY_BATCH_SIZE = 5_000;

    private final MjcSourceCsvReader reader;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final TranscriptSnapshotReplacementService snapshotReplacementService;

    @Async("sourceImportExecutor")
    public void process(
        Long importId,
        Long universityId,
        int admissionYear,
        Path directory,
        Path applicantsFile,
        Path baseInfoFile,
        Path subjectScoreFile
    ) {
        try {
            transactionTemplate.executeWithoutResult(status -> importCsvBundle(
                importId, universityId, admissionYear, applicantsFile, baseInfoFile, subjectScoreFile
            ));
        } catch (Exception exception) {
            log.error("MJC source CSV import failed: importId={}", importId, exception);
            deleteSnapshotsSafely(importId);
            fail(importId, safeMessage(exception));
        } finally {
            MjcSourceImportService.deleteDirectory(directory);
        }
    }

    private void importCsvBundle(
        Long importId,
        Long universityId,
        int admissionYear,
        Path applicantsFile,
        Path baseInfoFile,
        Path subjectScoreFile
    ) {
        List<ApplicantRow> applicants = reader.readApplicants(applicantsFile);
        Map<String, ApplicantRow> applicantsByNumber = applicants.stream().collect(Collectors.toMap(
            ApplicantRow::examNumber, Function.identity(),
            (first, ignored) -> { throw new IllegalArgumentException("지원자정보에 중복 수험번호가 있습니다."); },
            LinkedHashMap::new
        ));
        Map<String, BaseInfoRow> baseInfo = reader.readBaseInfo(baseInfoFile);
        if (!baseInfo.keySet().equals(applicantsByNumber.keySet())) {
            throw new IllegalArgumentException("지원자정보와 학생부 기본정보의 수험번호 구성이 일치하지 않습니다.");
        }

        updateStatus(importId, "PROCESSING", null);
        jdbcTemplate.update(
            "UPDATE student_transcript_import SET source_admission_year=? WHERE id=?", admissionYear, importId);
        deleteSnapshots(importId);
        TranscriptSnapshotReplacementService.SnapshotScope snapshot = snapshotReplacementService.clear(
            universityId, admissionYear, false
        );

        Map<UnitKey, Long> unitIds = upsertCatalog(universityId, admissionYear, applicants);
        upsertStudents(universityId, admissionYear, applicants, baseInfo);
        List<String> applicantNumbers = new ArrayList<>(applicantsByNumber.keySet());
        Map<String, Long> studentIds = loadStudentIds(universityId, admissionYear, applicantNumbers);
        if (!studentIds.keySet().containsAll(applicantsByNumber.keySet())) {
            throw new IllegalStateException("일부 지원자 기본정보를 생성하지 못했습니다.");
        }
        insertApplications(importId, applicants, studentIds, unitIds);

        int[] processed = {0};
        StreamResult result = reader.streamCourses(subjectScoreFile, BATCH_SIZE, batch -> {
            verifyCourseApplicants(batch, studentIds);
            upsertCourses(batch, studentIds, importId);
            insertCourseSnapshots(batch, importId);
            processed[0] += batch.size();
            if (processed[0] % 50_000 < batch.size()) {
                updateProgress(importId, processed[0], 0);
            }
        });
        snapshotReplacementService.deleteMissingStudents(snapshot.existingStudents(), applicantsByNumber.keySet());
        String warning = result.skippedRows() == 0 ? null
            : "이수단위가 없거나 0 이하인 과목 " + result.skippedRows() + "건을 성적 반영에서 제외했습니다.";
        complete(importId, result.totalRows(), result.importedRows(), result.skippedRows(), warning);
        log.info("MJC source CSV import completed: importId={}, applicants={}, totalRows={}, importedRows={}, skippedRows={}",
            importId, applicants.size(), result.totalRows(), result.importedRows(), result.skippedRows());
    }

    private Map<UnitKey, Long> upsertCatalog(Long universityId, int admissionYear, List<ApplicantRow> applicants) {
        Map<UnitKey, Long> result = new HashMap<>();
        for (ApplicantRow applicant : applicants) {
            UnitKey key = new UnitKey(
                applicant.admissionTypeName(), applicant.recruitmentUnitCode(), applicant.recruitmentUnitName());
            if (result.containsKey(key)) continue;
            jdbcTemplate.update("""
                INSERT INTO admission_track (university_id, admission_year, name, active, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE active=TRUE, updated_at=CURRENT_TIMESTAMP(6)
                """, universityId, admissionYear, key.trackName());
            Long trackId = jdbcTemplate.queryForObject(
                "SELECT id FROM admission_track WHERE university_id=? AND admission_year=? AND name=?",
                Long.class, universityId, admissionYear, key.trackName());
            jdbcTemplate.update("""
                INSERT INTO recruitment_unit (
                    admission_track_id, code, name, active, created_at, updated_at
                ) VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE code=VALUES(code), name=VALUES(name), active=TRUE,
                    updated_at=CURRENT_TIMESTAMP(6)
                """, trackId, key.unitCode(), key.unitName());
            Long unitId = jdbcTemplate.queryForObject(
                "SELECT id FROM recruitment_unit WHERE admission_track_id=? AND code=?",
                Long.class, trackId, key.unitCode());
            result.put(key, unitId);
        }
        return result;
    }

    private void upsertStudents(
        Long universityId,
        int admissionYear,
        List<ApplicantRow> applicants,
        Map<String, BaseInfoRow> baseInfo
    ) {
        String sql = """
            INSERT INTO student (
                university_id, admission_year, applicant_number, name, high_school_code, graduation_year,
                education_background, high_school_type, applicant_high_school_category_code,
                graduation_status, created_at, updated_at
            ) VALUES (?, ?, ?, '미등록', ?, ?, 'DOMESTIC_HIGH_SCHOOL', ?, ?, ?,
                CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE high_school_code=VALUES(high_school_code),
                graduation_year=VALUES(graduation_year), education_background='DOMESTIC_HIGH_SCHOOL',
                high_school_type=VALUES(high_school_type),
                applicant_high_school_category_code=VALUES(applicant_high_school_category_code),
                graduation_status=VALUES(graduation_status), updated_at=CURRENT_TIMESTAMP(6)
            """;
        jdbcTemplate.batchUpdate(sql, applicants, BATCH_SIZE, (statement, applicant) -> {
            BaseInfoRow base = baseInfo.get(applicant.examNumber());
            statement.setLong(1, universityId);
            statement.setInt(2, admissionYear);
            statement.setString(3, applicant.examNumber());
            setString(statement, 4, applicant.highSchoolCode());
            setInteger(statement, 5, graduationYear(applicant, base));
            statement.setString(6, highSchoolType(base));
            setString(statement, 7, base.applicantSchoolCode());
            statement.setString(8, graduationStatus(applicant));
        });
    }

    private Integer graduationYear(ApplicantRow applicant, BaseInfoRow base) {
        String date = applicant.graduationDate();
        if (date != null && date.length() >= 4) {
            try {
                return Integer.valueOf(date.substring(0, 4));
            } catch (NumberFormatException ignored) {
            }
        }
        return base.graduateYear();
    }

    private String highSchoolType(BaseInfoRow base) {
        if (Integer.valueOf(2).equals(base.graduateGrade())) return "TWO_YEAR";
        if ("Y".equalsIgnoreCase(base.specializedSchoolYn())) return "SPECIALIZED";
        return "GENERAL";
    }

    private String graduationStatus(ApplicantRow applicant) {
        return applicant.graduationStatus() != null && applicant.graduationStatus().contains("예정")
            ? "EXPECTED_GRADUATE" : "GRADUATE";
    }

    private Map<String, Long> loadStudentIds(
        Long universityId,
        int admissionYear,
        List<String> applicantNumbers
    ) {
        Map<String, Long> ids = new HashMap<>();
        for (int start = 0; start < applicantNumbers.size(); start += STUDENT_QUERY_BATCH_SIZE) {
            List<String> batch = applicantNumbers.subList(start,
                Math.min(start + STUDENT_QUERY_BATCH_SIZE, applicantNumbers.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            String sql = "SELECT id, applicant_number FROM student WHERE university_id=? AND admission_year=? "
                + "AND applicant_number IN (" + placeholders + ")";
            jdbcTemplate.query(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setLong(1, universityId);
                statement.setInt(2, admissionYear);
                for (int index = 0; index < batch.size(); index++) statement.setString(index + 3, batch.get(index));
                return statement;
            }, (org.springframework.jdbc.core.RowCallbackHandler) resultSet ->
                ids.put(resultSet.getString("applicant_number"), resultSet.getLong("id")));
        }
        return ids;
    }

    private void insertApplications(
        Long importId,
        List<ApplicantRow> applicants,
        Map<String, Long> studentIds,
        Map<UnitKey, Long> unitIds
    ) {
        String sql = """
            INSERT INTO student_application (
                student_id, source_import_id, recruitment_unit_id, created_at, updated_at
            ) VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE source_import_id=VALUES(source_import_id), updated_at=CURRENT_TIMESTAMP(6)
            """;
        jdbcTemplate.batchUpdate(sql, applicants, BATCH_SIZE, (statement, applicant) -> {
            statement.setLong(1, studentIds.get(applicant.examNumber()));
            statement.setLong(2, importId);
            statement.setLong(3, unitIds.get(new UnitKey(
                applicant.admissionTypeName(), applicant.recruitmentUnitCode(), applicant.recruitmentUnitName())));
        });
    }

    private void verifyCourseApplicants(List<CourseRow> rows, Map<String, Long> studentIds) {
        if (rows.stream().anyMatch(row -> !studentIds.containsKey(row.examNumber()))) {
            throw new IllegalArgumentException("교과자료에 지원자정보와 연결되지 않는 수험번호가 있습니다.");
        }
    }

    private void upsertCourses(List<CourseRow> rows, Map<String, Long> studentIds, Long importId) {
        String sql = """
            INSERT INTO student_transcript_course (
                student_id, source_import_id, school_year, semester, subject_category, course_name,
                course_name_normalized, grade_value, grade_scale, achievement, raw_score, mean_score,
                standard_deviation, student_count, rank_position, tied_rank_count, legacy_achievement,
                credits, career_subject, professional_course, source_file_name, source_row_number,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'NINE_LEVEL', ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?,
                '05_교과학습발달상황_SubjectScore.csv', ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE source_import_id=VALUES(source_import_id), subject_category=VALUES(subject_category),
                grade_value=VALUES(grade_value), achievement=VALUES(achievement), raw_score=VALUES(raw_score),
                mean_score=VALUES(mean_score), standard_deviation=VALUES(standard_deviation),
                student_count=VALUES(student_count), rank_position=VALUES(rank_position),
                tied_rank_count=VALUES(tied_rank_count), credits=VALUES(credits),
                career_subject=VALUES(career_subject), professional_course=VALUES(professional_course),
                source_file_name=VALUES(source_file_name), source_row_number=VALUES(source_row_number),
                updated_at=CURRENT_TIMESTAMP(6)
            """;
        jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (statement, row) -> bindCourse(
            statement, row, studentIds.get(row.examNumber()), importId, true
        ));
    }

    private void insertCourseSnapshots(List<CourseRow> rows, Long importId) {
        String sql = """
            INSERT INTO student_transcript_import_course (
                import_id, source_row_number, applicant_number, school_year, semester, subject_category,
                course_name, grade_value, grade_scale, achievement, raw_score, mean_score, standard_deviation,
                student_count, rank_position, tied_rank_count, legacy_achievement, credits, career_subject,
                professional_course, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'NINE_LEVEL', ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, CURRENT_TIMESTAMP(6))
            """;
        jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (statement, row) -> bindCourse(
            statement, row, null, importId, false
        ));
    }

    private void bindCourse(
        PreparedStatement statement,
        CourseRow row,
        Long studentId,
        Long importId,
        boolean currentCourse
    ) throws java.sql.SQLException {
        int index = 1;
        if (currentCourse) {
            statement.setLong(index++, studentId);
            statement.setLong(index++, importId);
            statement.setInt(index++, row.schoolYear());
            statement.setInt(index++, row.semester());
            statement.setString(index++, row.subjectCategory().name());
            statement.setString(index++, row.courseName());
            statement.setString(index++, TextNormalizer.normalizeCourseName(row.courseName()));
            setInteger(statement, index++, row.grade());
        } else {
            statement.setLong(index++, importId);
            statement.setInt(index++, row.rowNumber());
            statement.setString(index++, row.examNumber());
            statement.setInt(index++, row.schoolYear());
            statement.setInt(index++, row.semester());
            statement.setString(index++, row.subjectCategory().name());
            statement.setString(index++, row.courseName());
            setInteger(statement, index++, row.grade());
        }
        setString(statement, index++, row.achievement() == null ? null : row.achievement().name());
        setDecimal(statement, index++, row.rawScore());
        setDecimal(statement, index++, row.meanScore());
        setDecimal(statement, index++, row.standardDeviation());
        setInteger(statement, index++, row.studentCount());
        setInteger(statement, index++, row.rankPosition());
        setInteger(statement, index++, row.tiedRankCount());
        statement.setBigDecimal(index++, row.credits());
        statement.setBoolean(index++, row.careerSubject());
        statement.setBoolean(index++, row.professionalCourse());
        if (currentCourse) statement.setInt(index, row.rowNumber());
    }

    private void updateStatus(Long importId, String status, String errorMessage) {
        jdbcTemplate.update(
            "UPDATE student_transcript_import SET status=?, error_message=?, updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
            status, errorMessage, importId);
    }

    private void updateProgress(Long importId, int importedRows, int failedRows) {
        jdbcTemplate.update(
            "UPDATE student_transcript_import SET imported_rows=?, failed_rows=?, updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
            importedRows, failedRows, importId);
    }

    private void complete(Long importId, int totalRows, int importedRows, int failedRows, String warning) {
        String status = failedRows == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS";
        jdbcTemplate.update("""
            UPDATE student_transcript_import
            SET total_rows=?, imported_rows=?, failed_rows=?, status=?, error_message=?, temporary_file_path=NULL,
                updated_at=CURRENT_TIMESTAMP(6)
            WHERE id=?
            """, totalRows, importedRows, failedRows, status, warning, importId);
    }

    private void fail(Long importId, String message) {
        jdbcTemplate.update("""
            UPDATE student_transcript_import
            SET status='FAILED', error_message=?, temporary_file_path=NULL, updated_at=CURRENT_TIMESTAMP(6)
            WHERE id=?
            """, message, importId);
    }

    private void deleteSnapshots(Long importId) {
        jdbcTemplate.update("DELETE FROM student_transcript_import_course WHERE import_id=?", importId);
    }

    private void deleteSnapshotsSafely(Long importId) {
        try {
            deleteSnapshots(importId);
        } catch (Exception exception) {
            log.warn("Could not remove partial MJC import snapshots: importId={}", importId, exception);
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = "명지전문대 원천 CSV 처리 중 오류가 발생했습니다.";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
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

    private record UnitKey(String trackName, String unitCode, String unitName) {}
}
