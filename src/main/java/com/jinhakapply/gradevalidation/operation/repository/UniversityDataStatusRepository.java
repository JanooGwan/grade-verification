package com.jinhakapply.gradevalidation.operation.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UniversityDataStatusRepository {
    private final JdbcTemplate jdbcTemplate;

    public List<UniversityDataStatusProjection> findAll() {
        return jdbcTemplate.query("""
            SELECT university.id AS university_id,
                   university.code AS university_code,
                   university.name AS university_name,
                   university.active,
                   data_scope.admission_year,
                   COALESCE((
                       SELECT COUNT(*)
                       FROM student
                       WHERE student.university_id = university.id
                         AND student.admission_year = data_scope.admission_year
                   ), 0) AS student_count,
                   COALESCE((
                       SELECT COUNT(*)
                       FROM student_transcript_course course
                       JOIN student ON student.id = course.student_id
                       WHERE student.university_id = university.id
                         AND student.admission_year = data_scope.admission_year
                   ), 0) AS transcript_course_count,
                   COALESCE((
                       SELECT COUNT(*)
                       FROM student_application application
                       JOIN student ON student.id = application.student_id
                       WHERE student.university_id = university.id
                         AND student.admission_year = data_scope.admission_year
                   ), 0) AS application_count,
                   (
                       SELECT transcript_import.status
                       FROM student_transcript_import transcript_import
                       WHERE transcript_import.university_id = university.id
                         AND transcript_import.admission_year = data_scope.admission_year
                       ORDER BY transcript_import.created_at DESC, transcript_import.id DESC
                       LIMIT 1
                   ) AS latest_import_status,
                   (
                       SELECT transcript_import.original_file_name
                       FROM student_transcript_import transcript_import
                       WHERE transcript_import.university_id = university.id
                         AND transcript_import.admission_year = data_scope.admission_year
                       ORDER BY transcript_import.created_at DESC, transcript_import.id DESC
                       LIMIT 1
                   ) AS latest_import_file_name,
                   (
                       SELECT transcript_import.updated_at
                       FROM student_transcript_import transcript_import
                       WHERE transcript_import.university_id = university.id
                         AND transcript_import.admission_year = data_scope.admission_year
                       ORDER BY transcript_import.created_at DESC, transcript_import.id DESC
                       LIMIT 1
                   ) AS latest_import_at,
                   COALESCE((
                       SELECT COUNT(*)
                       FROM verification_run verification
                       JOIN student ON student.id = verification.student_id
                       WHERE student.university_id = university.id
                         AND student.admission_year = data_scope.admission_year
                   ), 0) AS verification_result_count,
                   (
                       SELECT MAX(verification.created_at)
                       FROM verification_run verification
                       JOIN student ON student.id = verification.student_id
                       WHERE student.university_id = university.id
                         AND student.admission_year = data_scope.admission_year
                   ) AS latest_verification_at
            FROM university
            LEFT JOIN (
                SELECT university_id, admission_year FROM student_transcript_import
                UNION
                SELECT university_id, admission_year FROM student
            ) data_scope ON data_scope.university_id = university.id
            ORDER BY university.active DESC, university.name, data_scope.admission_year DESC
            """, (resultSet, rowNumber) -> new UniversityDataStatusProjection(
                resultSet.getLong("university_id"),
                resultSet.getString("university_code"),
                resultSet.getString("university_name"),
                resultSet.getBoolean("active"),
                nullableInteger(resultSet, "admission_year"),
                resultSet.getLong("student_count"),
                resultSet.getLong("transcript_course_count"),
                resultSet.getLong("application_count"),
                resultSet.getString("latest_import_status"),
                resultSet.getString("latest_import_file_name"),
                timestamp(resultSet, "latest_import_at"),
                resultSet.getLong("verification_result_count"),
                timestamp(resultSet, "latest_verification_at")
            ));
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private java.time.LocalDateTime timestamp(ResultSet resultSet, String column) throws SQLException {
        var value = resultSet.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
