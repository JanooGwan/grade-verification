ALTER TABLE verification_run
    ADD COLUMN source_import_id BIGINT NULL AFTER id,
    ADD CONSTRAINT fk_verification_run_source_import
        FOREIGN KEY (source_import_id) REFERENCES student_transcript_import (id) ON DELETE CASCADE,
    ADD CONSTRAINT uk_verification_run_import_application_rule
        UNIQUE (source_import_id, application_id, rule_id, rule_version),
    ADD INDEX idx_verification_run_source_import (source_import_id, created_at);
