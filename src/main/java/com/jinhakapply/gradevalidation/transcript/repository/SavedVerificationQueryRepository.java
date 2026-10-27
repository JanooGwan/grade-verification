package com.jinhakapply.gradevalidation.transcript.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationBatchResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationResultRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SavedVerificationQueryRepository {
    private final JdbcTemplate jdbcTemplate;

    public java.util.Map<String, String[]> findMjcFailureReasons(Long importId) {
        java.util.Map<String, String[]> result = new java.util.HashMap<>();
        jdbcTemplate.query("""
            SELECT applicant_number, verification_code, verification_reason
            FROM student_mjc_archive_applicant WHERE import_id=? AND verification_code IS NOT NULL
            """, (org.springframework.jdbc.core.RowCallbackHandler) row -> result.put(row.getString("applicant_number"),
                new String[]{row.getString("verification_code"), row.getString("verification_reason")}), importId);
        return result;
    }

    /** 같은 업로드의 지원정보와 과목만 조회한다. */
    public List<ApplicationExportProjection> findExportApplications(Long sourceImportId) {
        return jdbcTemplate.query("""
            SELECT application.id, student.applicant_number, student.name AS student_name,
                   track.name AS track_name, unit.code AS unit_code, unit.name AS unit_name,
                   student.graduation_year, student.graduation_date, student.graduation_status,
                   student.education_background, student.high_school_type,
                   COALESCE(courses.course_count, 0) AS course_count
            FROM student_application application
            JOIN student ON student.id = application.student_id
            JOIN recruitment_unit unit ON unit.id = application.recruitment_unit_id
            JOIN admission_track track ON track.id = unit.admission_track_id
            LEFT JOIN (
                SELECT student_id, COUNT(*) AS course_count
                FROM student_transcript_course WHERE source_import_id = ? GROUP BY student_id
            ) courses ON courses.student_id = student.id
            WHERE application.source_import_id = ?
            ORDER BY application.id
            """, (rs, row) -> new ApplicationExportProjection(
                rs.getLong("id"), row + 1, rs.getString("applicant_number"), rs.getString("student_name"),
                rs.getString("track_name"), rs.getString("unit_code"), rs.getString("unit_name"),
                rs.getObject("graduation_year", Integer.class),
                rs.getDate("graduation_date") == null ? null : rs.getDate("graduation_date").toLocalDate(),
                rs.getString("graduation_status"), rs.getString("education_background"),
                rs.getString("high_school_type"), rs.getInt("course_count")
            ), sourceImportId, sourceImportId);
    }

    public record ApplicationExportProjection(
        Long applicationId, int rowNumber, String applicantNumber, String studentName,
        String trackName, String unitCode, String unitName, Integer graduationYear,
        java.time.LocalDate graduationDate, String graduationStatus, String educationBackground,
        String highSchoolType, int courseCount
    ) {}

    public List<SavedVerificationBatchResponse> findBatches(Long universityId, int admissionYear) {
        return jdbcTemplate.query("""
            SELECT transcript_import.id AS source_import_id,
                   university.id AS university_id,
                   university.name AS university_name,
                   transcript_import.admission_year,
                   transcript_import.original_file_name,
                   transcript_import.source_format,
                   COUNT(verification.id) AS result_count,
                   MAX(verification.created_at) AS saved_at
            FROM verification_run verification
            JOIN student_transcript_import transcript_import
              ON transcript_import.id = verification.source_import_id
            JOIN university ON university.id = transcript_import.university_id
            WHERE transcript_import.university_id = ?
              AND transcript_import.admission_year = ?
            GROUP BY transcript_import.id, university.id, university.name,
                     transcript_import.admission_year, transcript_import.original_file_name,
                     transcript_import.source_format
            ORDER BY saved_at DESC, transcript_import.id DESC
            """, (resultSet, ignored) -> new SavedVerificationBatchResponse(
                resultSet.getLong("source_import_id"),
                resultSet.getLong("university_id"),
                resultSet.getString("university_name"),
                resultSet.getInt("admission_year"),
                resultSet.getString("original_file_name"),
                resultSet.getString("source_format"),
                resultSet.getLong("result_count"),
                resultSet.getTimestamp("saved_at").toLocalDateTime()
            ), universityId, admissionYear);
    }

    public Optional<SavedVerificationBatchResponse> findBatch(Long sourceImportId) {
        List<SavedVerificationBatchResponse> results = jdbcTemplate.query("""
            SELECT transcript_import.id AS source_import_id,
                   university.id AS university_id,
                   university.name AS university_name,
                   transcript_import.admission_year,
                   transcript_import.original_file_name,
                   transcript_import.source_format,
                   COUNT(verification.id) AS result_count,
                   COALESCE(MAX(verification.created_at), MAX(transcript_import.updated_at)) AS saved_at
            FROM student_transcript_import transcript_import
            LEFT JOIN verification_run verification
              ON verification.source_import_id = transcript_import.id
            JOIN university ON university.id = transcript_import.university_id
            WHERE transcript_import.id = ?
              AND (verification.id IS NOT NULL OR
                   (transcript_import.source_format = 'MJC_CURRENT_SOURCE_ZIP_V1'
                    AND transcript_import.status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS')))
            GROUP BY transcript_import.id, university.id, university.name,
                     transcript_import.admission_year, transcript_import.original_file_name,
                     transcript_import.source_format
            """, (resultSet, ignored) -> new SavedVerificationBatchResponse(
                resultSet.getLong("source_import_id"),
                resultSet.getLong("university_id"),
                resultSet.getString("university_name"),
                resultSet.getInt("admission_year"),
                resultSet.getString("original_file_name"),
                resultSet.getString("source_format"),
                resultSet.getLong("result_count"),
                resultSet.getTimestamp("saved_at").toLocalDateTime()
            ), sourceImportId);
        return results.stream().findFirst();
    }

    public List<ExportProjection> findExportResultsAfter(
        Long sourceImportId,
        long afterVerificationRunId,
        int limit
    ) {
        return jdbcTemplate.query("""
            SELECT verification.id AS verification_run_id,
                   verification.rule_id,
                   application.id AS application_id,
                   student.applicant_number,
                   student.name AS student_name,
                   COALESCE(track.name, rule.admission_type) AS admission_track_name,
                   unit.code AS recruitment_unit_code,
                   COALESCE(unit.name, rule.recruitment_unit) AS recruitment_unit_name,
                   verification.result_json
            FROM verification_run verification
            JOIN student ON student.id = verification.student_id
            JOIN evaluation_rule rule ON rule.id = verification.rule_id
            LEFT JOIN student_application application ON application.id = verification.application_id
            LEFT JOIN recruitment_unit unit ON unit.id = application.recruitment_unit_id
            LEFT JOIN admission_track track ON track.id = unit.admission_track_id
            WHERE verification.source_import_id = ?
              AND verification.id > ?
            ORDER BY verification.id
            LIMIT ?
            """, (resultSet, ignored) -> new ExportProjection(
                resultSet.getLong("verification_run_id"),
                resultSet.getLong("rule_id"),
                resultSet.getObject("application_id", Long.class),
                resultSet.getString("applicant_number"),
                resultSet.getString("student_name"),
                resultSet.getString("admission_track_name"),
                resultSet.getString("recruitment_unit_code"),
                resultSet.getString("recruitment_unit_name"),
                resultSet.getString("result_json")
            ), sourceImportId, afterVerificationRunId, limit);
    }

    public void streamScenarioExportResults(
        Long sourceImportId,
        Consumer<ScenarioExportProjection> consumer
    ) {
        jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                SELECT student.applicant_number,
                       COALESCE(track.name, rule.admission_type) AS admission_track_name,
                       COALESCE(unit.name, rule.recruitment_unit) AS recruitment_unit_name,
                       verification.included_course_count,
                       verification.excluded_course_count,
                       verification.average_grade,
                       verification.final_score,
                       verification.export_summary_json,
                       CASE WHEN JSON_EXTRACT(verification.export_summary_json, '$.subjectDomains') IS NULL
                            THEN verification.result_json
                            ELSE NULL
                       END AS result_json
                FROM verification_run verification
                JOIN student ON student.id = verification.student_id
                JOIN evaluation_rule rule ON rule.id = verification.rule_id
                LEFT JOIN student_application application ON application.id = verification.application_id
                LEFT JOIN recruitment_unit unit ON unit.id = application.recruitment_unit_id
                LEFT JOIN admission_track track ON track.id = unit.admission_track_id
                WHERE verification.source_import_id = ?
                ORDER BY student.applicant_number, rule.admission_type, rule.recruitment_unit,
                         verification.id
                """, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            statement.setFetchSize(Integer.MIN_VALUE);
            statement.setLong(1, sourceImportId);
            return statement;
        }, resultSet -> {
            while (resultSet.next()) {
                consumer.accept(new ScenarioExportProjection(
                    resultSet.getString("applicant_number"),
                    resultSet.getString("admission_track_name"),
                    resultSet.getString("recruitment_unit_name"),
                    resultSet.getInt("included_course_count"),
                    resultSet.getInt("excluded_course_count"),
                    resultSet.getBigDecimal("average_grade"),
                    resultSet.getBigDecimal("final_score"),
                    resultSet.getString("export_summary_json"),
                    resultSet.getString("result_json")
                ));
            }
            return null;
        });
    }

    public long countResults(Long sourceImportId, String keyword) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM verification_run verification
            JOIN student ON student.id = verification.student_id
            JOIN evaluation_rule rule ON rule.id = verification.rule_id
            LEFT JOIN student_application application ON application.id = verification.application_id
            LEFT JOIN recruitment_unit unit ON unit.id = application.recruitment_unit_id
            LEFT JOIN admission_track track ON track.id = unit.admission_track_id
            WHERE verification.source_import_id = ?
              AND (? = '' OR student.applicant_number LIKE ? OR student.name LIKE ?
                   OR COALESCE(track.name, rule.admission_type) LIKE ?
                   OR COALESCE(unit.name, rule.recruitment_unit) LIKE ?)
            """, Long.class, sourceImportId, keyword, like(keyword), like(keyword), like(keyword), like(keyword));
    }

    public List<SavedVerificationResultRow> findResults(
        Long sourceImportId,
        String keyword,
        int limit,
        long offset
    ) {
        return jdbcTemplate.query("""
            SELECT verification.id AS verification_run_id,
                   student.id AS student_id,
                   student.applicant_number,
                   student.name AS student_name,
                   COALESCE(track.name, rule.admission_type) AS admission_track_name,
                   COALESCE(unit.name, rule.recruitment_unit) AS recruitment_unit_name,
                   rule.name AS rule_name,
                   verification.rule_version,
                   verification.final_score,
                   verification.average_grade,
                   verification.included_course_count,
                   verification.excluded_course_count,
                   verification.created_at AS saved_at
            FROM verification_run verification
            JOIN student ON student.id = verification.student_id
            JOIN evaluation_rule rule ON rule.id = verification.rule_id
            LEFT JOIN student_application application ON application.id = verification.application_id
            LEFT JOIN recruitment_unit unit ON unit.id = application.recruitment_unit_id
            LEFT JOIN admission_track track ON track.id = unit.admission_track_id
            WHERE verification.source_import_id = ?
              AND (? = '' OR student.applicant_number LIKE ? OR student.name LIKE ?
                   OR COALESCE(track.name, rule.admission_type) LIKE ?
                   OR COALESCE(unit.name, rule.recruitment_unit) LIKE ?)
            ORDER BY student.applicant_number, verification.id
            LIMIT ? OFFSET ?
            """, (resultSet, ignored) -> resultRow(resultSet),
            sourceImportId, keyword, like(keyword), like(keyword), like(keyword), like(keyword), limit, offset);
    }

    public Optional<DetailProjection> findDetail(Long verificationRunId) {
        List<DetailProjection> results = jdbcTemplate.query("""
            SELECT verification.id AS verification_run_id,
                   verification.source_import_id,
                   student.id AS student_id,
                   student.applicant_number,
                   student.name AS student_name,
                   verification.created_at AS saved_at,
                   verification.result_json
            FROM verification_run verification
            JOIN student ON student.id = verification.student_id
            WHERE verification.id = ?
              AND verification.source_import_id IS NOT NULL
            """, (resultSet, ignored) -> new DetailProjection(
                resultSet.getLong("verification_run_id"),
                resultSet.getLong("source_import_id"),
                resultSet.getLong("student_id"),
                resultSet.getString("applicant_number"),
                resultSet.getString("student_name"),
                resultSet.getTimestamp("saved_at").toLocalDateTime(),
                resultSet.getString("result_json")
            ), verificationRunId);
        return results.stream().findFirst();
    }

    private SavedVerificationResultRow resultRow(ResultSet resultSet) throws SQLException {
        return new SavedVerificationResultRow(
            resultSet.getLong("verification_run_id"),
            resultSet.getLong("student_id"),
            resultSet.getString("applicant_number"),
            resultSet.getString("student_name"),
            resultSet.getString("admission_track_name"),
            resultSet.getString("recruitment_unit_name"),
            resultSet.getString("rule_name"),
            resultSet.getInt("rule_version"),
            resultSet.getBigDecimal("final_score"),
            resultSet.getBigDecimal("average_grade"),
            resultSet.getInt("included_course_count"),
            resultSet.getInt("excluded_course_count"),
            resultSet.getTimestamp("saved_at").toLocalDateTime()
        );
    }

    private String like(String keyword) {
        return "%" + keyword + "%";
    }

    public record DetailProjection(
        Long verificationRunId,
        Long sourceImportId,
        Long studentId,
        String applicantNumber,
        String studentName,
        java.time.LocalDateTime savedAt,
        String resultJson
    ) {}

    public record ExportProjection(
        Long verificationRunId,
        Long ruleId,
        Long applicationId,
        String applicantNumber,
        String studentName,
        String admissionTrackName,
        String recruitmentUnitCode,
        String recruitmentUnitName,
        String resultJson
    ) {}

    public record ScenarioExportProjection(
        String applicantNumber,
        String admissionTrackName,
        String recruitmentUnitName,
        int includedCourseCount,
        int excludedCourseCount,
        java.math.BigDecimal averageGrade,
        java.math.BigDecimal finalScore,
        String exportSummaryJson,
        String resultJson
    ) {}
}
