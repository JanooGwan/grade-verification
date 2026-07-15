ALTER TABLE evaluation_rule
    ADD COLUMN selection_strategy VARCHAR(40) NOT NULL DEFAULT 'ALL_COURSES',
    ADD COLUMN selection_count INT NOT NULL DEFAULT 0,
    ADD COLUMN achievement_selection_count INT NOT NULL DEFAULT 0,
    ADD COLUMN score_aggregation VARCHAR(40) NOT NULL DEFAULT 'COURSE_SCORE_AVERAGE',
    ADD COLUMN achievement_conversion VARCHAR(30) NOT NULL DEFAULT 'DIRECT_TABLE',
    ADD COLUMN include_third_year_second_semester BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN include_professional_courses BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN normalize_grade_weights BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN intermediate_scale INT NOT NULL DEFAULT 5,
    ADD COLUMN intermediate_rounding VARCHAR(20) NOT NULL DEFAULT 'HALF_UP',
    ADD COLUMN final_scale INT NOT NULL DEFAULT 4,
    ADD COLUMN final_rounding VARCHAR(20) NOT NULL DEFAULT 'HALF_UP',
    ADD COLUMN score_multiplier DECIMAL(9,4) NOT NULL DEFAULT 1.0000,
    ADD COLUMN source_document VARCHAR(255) NULL,
    ADD COLUMN source_pages VARCHAR(50) NULL;

CREATE TABLE evaluation_rule_achievement_grade (
    rule_id BIGINT NOT NULL,
    achievement_level VARCHAR(1) NOT NULL,
    converted_grade DECIMAL(5,2) NOT NULL,
    CONSTRAINT pk_evaluation_rule_achievement_grade PRIMARY KEY (rule_id, achievement_level),
    CONSTRAINT fk_achievement_grade_rule FOREIGN KEY (rule_id) REFERENCES evaluation_rule (id) ON DELETE CASCADE
);

CREATE TABLE evaluation_rule_achievement_score (
    rule_id BIGINT NOT NULL,
    achievement_level VARCHAR(1) NOT NULL,
    converted_score DECIMAL(7,4) NOT NULL,
    CONSTRAINT pk_evaluation_rule_achievement_score PRIMARY KEY (rule_id, achievement_level),
    CONSTRAINT fk_achievement_score_rule FOREIGN KEY (rule_id) REFERENCES evaluation_rule (id) ON DELETE CASCADE
);

CREATE TABLE evaluation_rule_subject_priority (
    rule_id BIGINT NOT NULL,
    subject_category VARCHAR(20) NOT NULL,
    priority_value INT NOT NULL,
    CONSTRAINT pk_evaluation_rule_subject_priority PRIMARY KEY (rule_id, subject_category),
    CONSTRAINT fk_subject_priority_rule FOREIGN KEY (rule_id) REFERENCES evaluation_rule (id) ON DELETE CASCADE
);

INSERT INTO evaluation_rule_achievement_grade (rule_id, achievement_level, converted_grade)
SELECT id, 'A', 1.00 FROM evaluation_rule
UNION ALL SELECT id, 'B', 3.00 FROM evaluation_rule
UNION ALL SELECT id, 'C', 5.00 FROM evaluation_rule;

INSERT INTO evaluation_rule_achievement_score (rule_id, achievement_level, converted_score)
SELECT id, 'A', 100.0000 FROM evaluation_rule
UNION ALL SELECT id, 'B', 99.0000 FROM evaluation_rule
UNION ALL SELECT id, 'C', 98.0000 FROM evaluation_rule;

INSERT INTO evaluation_rule_subject_priority (rule_id, subject_category, priority_value)
SELECT id, 'KOREAN', 3 FROM evaluation_rule
UNION ALL SELECT id, 'MATH', 2 FROM evaluation_rule
UNION ALL SELECT id, 'ENGLISH', 4 FROM evaluation_rule
UNION ALL SELECT id, 'SOCIAL', 5 FROM evaluation_rule
UNION ALL SELECT id, 'SCIENCE', 1 FROM evaluation_rule
UNION ALL SELECT id, 'OTHER', 6 FROM evaluation_rule;
