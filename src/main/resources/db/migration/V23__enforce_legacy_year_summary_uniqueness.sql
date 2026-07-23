ALTER TABLE student_legacy_grade_summary
    DROP INDEX uk_student_legacy_grade_summary,
    ADD COLUMN semester_key INT GENERATED ALWAYS AS (COALESCE(semester, 0)) STORED AFTER semester,
    ADD CONSTRAINT uk_student_legacy_grade_summary
        UNIQUE (student_id, summary_type, school_year, semester_key);
