package com.jinhakapply.gradevalidation.operation.repository;

import java.util.List;
import com.jinhakapply.gradevalidation.operation.dto.LightDataStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LightDataStatusRepository {
    private final JdbcTemplate jdbc;

    /** Only import metadata and indexed existence/latest lookups; never count the course table. */
    public List<LightDataStatus> findAll() {
        return jdbc.query("""
            WITH scopes AS (
                SELECT university_id, admission_year FROM student_transcript_import
                UNION SELECT university_id, admission_year FROM student
            ), verification_times AS (
                SELECT i.university_id, i.admission_year,
                    MAX((SELECT MAX(v.created_at) FROM verification_run v
                         WHERE v.source_import_id = i.id)) AS latest_verified
                FROM student_transcript_import i GROUP BY i.university_id, i.admission_year
            )
            SELECT u.id university_id, u.code university_code, u.name university_name, u.active,
                   s.admission_year,
                   EXISTS(SELECT 1 FROM student st WHERE st.university_id=u.id
                          AND st.admission_year=s.admission_year LIMIT 1) student_present,
                   (SELECT i.status FROM student_transcript_import i WHERE i.university_id=u.id
                    AND i.admission_year=s.admission_year ORDER BY i.created_at DESC, i.id DESC LIMIT 1) import_status,
                   (SELECT i.updated_at FROM student_transcript_import i WHERE i.university_id=u.id
                    AND i.admission_year=s.admission_year ORDER BY i.created_at DESC, i.id DESC LIMIT 1) import_time,
                   v.latest_verified
            FROM university u LEFT JOIN scopes s ON s.university_id=u.id
            LEFT JOIN verification_times v ON v.university_id=u.id AND v.admission_year=s.admission_year
            ORDER BY u.active DESC, u.name, s.admission_year DESC
            """, (rs, row) -> {
                var imported = rs.getTimestamp("import_time");
                var verified = rs.getTimestamp("latest_verified");
                return new LightDataStatus(rs.getLong("university_id"), rs.getString("university_code"),
                    rs.getString("university_name"), rs.getBoolean("active"),
                    rs.getObject("admission_year", Integer.class), rs.getBoolean("student_present"),
                    rs.getString("import_status"), imported == null ? null : imported.toLocalDateTime(),
                    verified != null, verified == null ? null : verified.toLocalDateTime());
            });
    }
}
