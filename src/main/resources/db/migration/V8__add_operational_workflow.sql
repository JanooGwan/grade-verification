ALTER TABLE student_transcript_import
    ADD COLUMN import_mode VARCHAR(30) NOT NULL DEFAULT 'VALID_ROWS_ONLY' AFTER original_file_name,
    ADD COLUMN file_sha256 CHAR(64) NULL AFTER import_mode;

CREATE INDEX idx_transcript_import_created
    ON student_transcript_import (created_at DESC);

CREATE INDEX idx_rule_extraction_hash
    ON evaluation_rule_extraction (university_id, admission_year, file_sha256);

CREATE TABLE verification_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    application_id BIGINT NULL,
    rule_id BIGINT NOT NULL,
    rule_version INT NOT NULL,
    final_score DECIMAL(14,6) NOT NULL,
    average_grade DECIMAL(10,6) NOT NULL,
    included_course_count INT NOT NULL,
    excluded_course_count INT NOT NULL,
    result_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_verification_run PRIMARY KEY (id),
    CONSTRAINT fk_verification_run_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE,
    CONSTRAINT fk_verification_run_application FOREIGN KEY (application_id) REFERENCES student_application (id) ON DELETE SET NULL,
    CONSTRAINT fk_verification_run_rule FOREIGN KEY (rule_id) REFERENCES evaluation_rule (id)
);

CREATE INDEX idx_verification_run_student_created
    ON verification_run (student_id, created_at DESC);

CREATE INDEX idx_verification_run_application_created
    ON verification_run (application_id, created_at DESC);
