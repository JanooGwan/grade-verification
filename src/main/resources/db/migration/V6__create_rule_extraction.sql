CREATE TABLE evaluation_rule_extraction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    university_id BIGINT NOT NULL,
    admission_year INT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    page_count INT NOT NULL,
    text_page_count INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    selection_strategy VARCHAR(40),
    selection_count INT,
    grade_weights_csv VARCHAR(100),
    grade_scores_csv VARCHAR(255),
    achievement_scores_csv VARCHAR(100),
    subject_categories_csv VARCHAR(100),
    include_third_year_second_semester BOOLEAN,
    rounding_mode VARCHAR(20),
    source_pages VARCHAR(255),
    overall_confidence DECIMAL(5,4) NOT NULL,
    missing_fields VARCHAR(500),
    warnings VARCHAR(2000),
    draft_rule_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_evaluation_rule_extraction PRIMARY KEY (id),
    CONSTRAINT fk_rule_extraction_university FOREIGN KEY (university_id) REFERENCES university (id),
    CONSTRAINT fk_rule_extraction_draft_rule FOREIGN KEY (draft_rule_id) REFERENCES evaluation_rule (id)
);

CREATE INDEX idx_rule_extraction_university
    ON evaluation_rule_extraction (university_id, admission_year, created_at);

CREATE TABLE evaluation_rule_extraction_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    extraction_id BIGINT NOT NULL,
    field_key VARCHAR(50) NOT NULL,
    page_number INT NOT NULL,
    excerpt VARCHAR(1500) NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    CONSTRAINT pk_rule_extraction_evidence PRIMARY KEY (id),
    CONSTRAINT fk_rule_extraction_evidence FOREIGN KEY (extraction_id)
        REFERENCES evaluation_rule_extraction (id) ON DELETE CASCADE
);

CREATE INDEX idx_rule_extraction_evidence
    ON evaluation_rule_extraction_evidence (extraction_id, field_key, page_number);

ALTER TABLE evaluation_rule
    ADD COLUMN extraction_id BIGINT NULL AFTER change_summary,
    ADD CONSTRAINT fk_evaluation_rule_extraction FOREIGN KEY (extraction_id)
        REFERENCES evaluation_rule_extraction (id);
