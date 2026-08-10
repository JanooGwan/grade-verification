package com.jinhakapply.gradevalidation.admission.repository;

import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import lombok.RequiredArgsConstructor;
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
}
