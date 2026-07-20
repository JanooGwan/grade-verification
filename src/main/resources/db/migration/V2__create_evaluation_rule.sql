CREATE TABLE evaluation_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    university_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    admission_year INT NOT NULL,
    admission_type VARCHAR(100) NOT NULL,
    recruitment_unit VARCHAR(120) NOT NULL,
    version INT NOT NULL,
    grade1_weight DECIMAL(7,4) NOT NULL,
    grade2_weight DECIMAL(7,4) NOT NULL,
    grade3_weight DECIMAL(7,4) NOT NULL,
    korean_weight DECIMAL(7,4) NOT NULL,
    math_weight DECIMAL(7,4) NOT NULL,
    english_weight DECIMAL(7,4) NOT NULL,
    social_weight DECIMAL(7,4) NOT NULL,
    science_weight DECIMAL(7,4) NOT NULL,
    other_weight DECIMAL(7,4) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_evaluation_rule PRIMARY KEY (id),
    CONSTRAINT fk_evaluation_rule_university FOREIGN KEY (university_id) REFERENCES university (id),
    CONSTRAINT uk_evaluation_rule_version UNIQUE (university_id, admission_year, admission_type, recruitment_unit, version)
);

CREATE TABLE evaluation_rule_grade_score (
    rule_id BIGINT NOT NULL,
    grade_value INT NOT NULL,
    converted_score DECIMAL(7,4) NOT NULL,
    CONSTRAINT pk_evaluation_rule_grade_score PRIMARY KEY (rule_id, grade_value),
    CONSTRAINT fk_grade_score_rule FOREIGN KEY (rule_id) REFERENCES evaluation_rule (id) ON DELETE CASCADE,
    CONSTRAINT ck_grade_value CHECK (grade_value BETWEEN 1 AND 9)
);

CREATE INDEX idx_evaluation_rule_university ON evaluation_rule (university_id, active);
