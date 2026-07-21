CREATE TABLE application_score_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    rule_version INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    education_background VARCHAR(40) NOT NULL,
    academic_base_score DECIMAL(14,6) NOT NULL,
    academic_score DECIMAL(14,6) NOT NULL,
    attendance_score DECIMAL(14,6),
    additional_score DECIMAL(14,6),
    school_violence_deduction DECIMAL(14,6) NOT NULL,
    quantitative_subtotal DECIMAL(14,6) NOT NULL,
    score_after_deduction DECIMAL(14,6) NOT NULL,
    final_score DECIMAL(14,6),
    result_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_application_score_run PRIMARY KEY (id),
    CONSTRAINT fk_application_score_run_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE,
    CONSTRAINT fk_application_score_run_application FOREIGN KEY (application_id) REFERENCES student_application (id) ON DELETE CASCADE,
    CONSTRAINT fk_application_score_run_rule FOREIGN KEY (rule_id) REFERENCES evaluation_rule (id)
);

CREATE INDEX idx_application_score_run_application_created
    ON application_score_run (application_id, created_at DESC);
