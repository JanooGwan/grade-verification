package com.jinhakapply.gradevalidation.transcript.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationBatchResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationResultRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SavedVerificationQueryRepository {
    private final JdbcTemplate jdbcTemplate;

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

    public long countResults(Long sourceImportId, String keyword) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM verification_run verification
            JOIN student ON student.id = verification.student_id
            JOIN student_application application ON application.id = verification.application_id
            JOIN recruitment_unit unit ON unit.id = application.recruitment_unit_id
            JOIN admission_track track ON track.id = unit.admission_track_id
            WHERE verification.source_import_id = ?
              AND (? = '' OR student.applicant_number LIKE ? OR student.name LIKE ?
                   OR track.name LIKE ? OR unit.name LIKE ?)
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
                   track.name AS admission_track_name,
                   unit.name AS recruitment_unit_name,
                   rule.name AS rule_name,
                   verification.rule_version,
                   verification.final_score,
                   verification.average_grade,
                   verification.included_course_count,
                   verification.excluded_course_count,
                   verification.created_at AS saved_at
            FROM verification_run verification
            JOIN student ON student.id = verification.student_id
            JOIN student_application application ON application.id = verification.application_id
            JOIN recruitment_unit unit ON unit.id = application.recruitment_unit_id
            JOIN admission_track track ON track.id = unit.admission_track_id
            JOIN evaluation_rule rule ON rule.id = verification.rule_id
            WHERE verification.source_import_id = ?
              AND (? = '' OR student.applicant_number LIKE ? OR student.name LIKE ?
                   OR track.name LIKE ? OR unit.name LIKE ?)
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
}
