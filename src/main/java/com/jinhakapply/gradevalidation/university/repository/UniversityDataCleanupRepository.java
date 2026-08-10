package com.jinhakapply.gradevalidation.university.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UniversityDataCleanupRepository {
    private final JdbcTemplate jdbcTemplate;

    public void deleteAllByUniversityId(Long universityId) {
        deleteRuleRuns(universityId);
        deleteStudentsAndCatalog(universityId);
        deleteImports(universityId);
        deleteRulesAndExtractions(universityId);
        deleteProfiles(universityId);
    }

    private void deleteRuleRuns(Long universityId) {
        jdbcTemplate.update("""
            DELETE FROM application_score_run
            WHERE rule_id IN (SELECT id FROM evaluation_rule WHERE university_id = ?)
            """, universityId);
        jdbcTemplate.update("""
            DELETE FROM verification_run
            WHERE rule_id IN (SELECT id FROM evaluation_rule WHERE university_id = ?)
            """, universityId);
    }

    private void deleteStudentsAndCatalog(Long universityId) {
        jdbcTemplate.update("DELETE FROM student WHERE university_id = ?", universityId);
        jdbcTemplate.update("""
            DELETE FROM student_application
            WHERE recruitment_unit_id IN (
                SELECT unit.id
                FROM recruitment_unit unit
                JOIN admission_track track ON track.id = unit.admission_track_id
                WHERE track.university_id = ?
            )
            """, universityId);
        jdbcTemplate.update("""
            DELETE FROM recruitment_unit
            WHERE admission_track_id IN (SELECT id FROM admission_track WHERE university_id = ?)
            """, universityId);
        jdbcTemplate.update("DELETE FROM admission_track WHERE university_id = ?", universityId);
    }

    private void deleteImports(Long universityId) {
        jdbcTemplate.update("DELETE FROM student_transcript_import WHERE university_id = ?", universityId);
    }

    private void deleteRulesAndExtractions(Long universityId) {
        jdbcTemplate.update("""
            UPDATE evaluation_rule
            SET extraction_id = NULL
            WHERE university_id = ?
               OR extraction_id IN (
                   SELECT id FROM evaluation_rule_extraction WHERE university_id = ?
               )
            """, universityId, universityId);
        jdbcTemplate.update("""
            UPDATE evaluation_rule_extraction
            SET draft_rule_id = NULL
            WHERE university_id = ?
               OR draft_rule_id IN (
                   SELECT id FROM evaluation_rule WHERE university_id = ?
               )
            """, universityId, universityId);
        jdbcTemplate.update("DELETE FROM evaluation_rule_extraction WHERE university_id = ?", universityId);
        jdbcTemplate.update("DELETE FROM evaluation_rule WHERE university_id = ?", universityId);
    }

    private void deleteProfiles(Long universityId) {
        jdbcTemplate.update("DELETE FROM university_import_profile WHERE university_id = ?", universityId);
        jdbcTemplate.update("DELETE FROM university_export_profile WHERE university_id = ?", universityId);
    }
}
