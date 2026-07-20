UPDATE evaluation_rule
SET status = 'RETIRED',
    retired_at = COALESCE(updated_at, created_at)
WHERE active = FALSE
  AND status = 'PUBLISHED'
  AND published_at IS NULL;

ALTER TABLE evaluation_rule
    ALTER COLUMN status SET DEFAULT 'DRAFT';

ALTER TABLE evaluation_rule_extraction
    ADD CONSTRAINT uk_rule_extraction_file
        UNIQUE (university_id, admission_year, file_sha256);
