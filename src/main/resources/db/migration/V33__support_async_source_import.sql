ALTER TABLE student_transcript_import
    ADD COLUMN source_format VARCHAR(50) NOT NULL DEFAULT 'STANDARD_TRANSCRIPT_V1' AFTER file_sha256,
    ADD COLUMN error_message VARCHAR(1000) NULL AFTER status,
    ADD COLUMN updated_at DATETIME(6) NULL AFTER created_at;

UPDATE student_transcript_import
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE student_transcript_import
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;
