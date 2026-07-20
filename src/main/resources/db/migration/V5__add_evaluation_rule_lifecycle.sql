ALTER TABLE evaluation_rule
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' AFTER active,
    ADD COLUMN interpretation_note VARCHAR(1000) NULL AFTER source_pages,
    ADD COLUMN change_summary VARCHAR(1000) NULL AFTER interpretation_note,
    ADD COLUMN reviewer VARCHAR(100) NULL AFTER status,
    ADD COLUMN review_note VARCHAR(1000) NULL AFTER reviewer,
    ADD COLUMN reviewed_at DATETIME(6) NULL AFTER review_note,
    ADD COLUMN published_by VARCHAR(100) NULL AFTER reviewed_at,
    ADD COLUMN publication_note VARCHAR(1000) NULL AFTER published_by,
    ADD COLUMN published_at DATETIME(6) NULL AFTER publication_note,
    ADD COLUMN retired_by VARCHAR(100) NULL AFTER published_at,
    ADD COLUMN retire_note VARCHAR(1000) NULL AFTER retired_by,
    ADD COLUMN retired_at DATETIME(6) NULL AFTER retire_note;

UPDATE evaluation_rule
SET status = 'PUBLISHED',
    published_at = updated_at
WHERE active = TRUE;

CREATE INDEX idx_evaluation_rule_status
    ON evaluation_rule (status, admission_year, university_id);
