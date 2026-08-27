package com.jinhakapply.gradevalidation.admission.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BatchVerificationRunRepository {
    private final JdbcTemplate jdbcTemplate;

    public int deleteAllBySourceImportId(Long sourceImportId) {
        return jdbcTemplate.update("DELETE FROM verification_run WHERE source_import_id = ?", sourceImportId);
    }

    public int insert(
        Long sourceImportId,
        Long applicationId,
        GradeVerificationResponse result,
        String resultJson
    ) {
        return jdbcTemplate.update("""
            INSERT INTO verification_run (
                source_import_id, student_id, application_id, rule_id, rule_version,
                final_score, average_grade, included_course_count, excluded_course_count,
                result_json, created_at
            )
            SELECT ?, application.student_id, application.id, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6)
            FROM student_application application
            WHERE application.id = ?
            """,
            sourceImportId,
            result.ruleId(),
            result.ruleVersion(),
            result.finalScore(),
            result.averageGrade(),
            result.includedCourseCount(),
            result.excludedCourseCount(),
            resultJson,
            applicationId
        );
    }

    public int insertScenarios(Long sourceImportId, List<ScenarioVerificationRow> rows) {
        if (rows.isEmpty()) return 0;
        int[] counts = jdbcTemplate.batchUpdate("""
            INSERT INTO verification_run (
                source_import_id, student_id, application_id, rule_id, rule_version,
                final_score, average_grade, included_course_count, excluded_course_count,
                result_json, export_summary_json, created_at
            ) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
            """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int index) throws SQLException {
                    ScenarioVerificationRow row = rows.get(index);
                    GradeVerificationResponse result = row.result();
                    statement.setLong(1, sourceImportId);
                    statement.setLong(2, row.studentId());
                    statement.setLong(3, result.ruleId());
                    statement.setInt(4, result.ruleVersion());
                    statement.setBigDecimal(5, result.finalScore());
                    statement.setBigDecimal(6, result.averageGrade());
                    statement.setInt(7, result.includedCourseCount());
                    statement.setInt(8, result.excludedCourseCount());
                    statement.setString(9, row.resultJson());
                    statement.setString(10, row.exportSummaryJson());
                }

                @Override
                public int getBatchSize() {
                    return rows.size();
                }
            });
        return Arrays.stream(counts).map(count -> count == Statement.EXECUTE_FAILED ? 0 : 1).sum();
    }

    public record ScenarioVerificationRow(
        Long studentId,
        GradeVerificationResponse result,
        String resultJson,
        String exportSummaryJson
    ) {}
}
