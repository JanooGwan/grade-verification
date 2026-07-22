ALTER TABLE student_transcript_course
    ADD COLUMN grade_scale VARCHAR(20) NOT NULL DEFAULT 'NINE_LEVEL' AFTER grade_value,
    ADD COLUMN rank_position INT NULL AFTER student_count,
    ADD COLUMN tied_rank_count INT NULL AFTER rank_position,
    ADD COLUMN legacy_achievement VARCHAR(10) NULL AFTER tied_rank_count;

ALTER TABLE evaluation_rule
    ADD COLUMN input_grade_scale VARCHAR(20) NOT NULL DEFAULT 'NINE_LEVEL' AFTER achievement_conversion;

CREATE TABLE evaluation_rule_legacy_achievement_grade (
    rule_id BIGINT NOT NULL,
    legacy_achievement VARCHAR(10) NOT NULL,
    converted_grade DECIMAL(5,2) NOT NULL,
    CONSTRAINT pk_rule_legacy_achievement_grade PRIMARY KEY (rule_id, legacy_achievement),
    CONSTRAINT fk_rule_legacy_achievement_grade_rule
        FOREIGN KEY (rule_id) REFERENCES evaluation_rule (id) ON DELETE CASCADE
);

INSERT INTO evaluation_rule_legacy_achievement_grade (rule_id, legacy_achievement, converted_grade)
SELECT id, 'SU', 1.00 FROM evaluation_rule
UNION ALL SELECT id, 'WOO', 3.00 FROM evaluation_rule
UNION ALL SELECT id, 'MI', 5.00 FROM evaluation_rule
UNION ALL SELECT id, 'YANG', 7.00 FROM evaluation_rule
UNION ALL SELECT id, 'GA', 9.00 FROM evaluation_rule;

CREATE TABLE student_ged_subject_score (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    subject_type VARCHAR(30) NOT NULL,
    subject_name VARCHAR(100) NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_student_ged_subject_score PRIMARY KEY (id),
    CONSTRAINT uk_student_ged_subject_score UNIQUE (student_id, subject_name),
    CONSTRAINT fk_student_ged_subject_score_student
        FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE,
    CONSTRAINT ck_student_ged_subject_score CHECK (score BETWEEN 0 AND 100)
);

CREATE TABLE student_legacy_grade_summary (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    summary_type VARCHAR(20) NOT NULL,
    school_year INT NOT NULL,
    semester INT NULL,
    rank_position INT NOT NULL,
    tied_rank_count INT NULL,
    cohort_size INT NOT NULL,
    credits DECIMAL(6,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_student_legacy_grade_summary PRIMARY KEY (id),
    CONSTRAINT uk_student_legacy_grade_summary UNIQUE (student_id, summary_type, school_year, semester),
    CONSTRAINT fk_student_legacy_grade_summary_student
        FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE,
    CONSTRAINT ck_student_legacy_summary_year CHECK (school_year BETWEEN 1 AND 3),
    CONSTRAINT ck_student_legacy_summary_semester CHECK (semester IS NULL OR semester BETWEEN 1 AND 2),
    CONSTRAINT ck_student_legacy_summary_rank CHECK (rank_position BETWEEN 1 AND cohort_size),
    CONSTRAINT ck_student_legacy_summary_tied CHECK (tied_rank_count IS NULL OR tied_rank_count >= 1),
    CONSTRAINT ck_student_legacy_summary_credits CHECK (credits > 0)
);

ALTER TABLE student_transcript_course COMMENT = '지원자의 과목별 학생부 성적. 현행 등급, 구교육과정 석차·동석차·평어를 함께 저장한다.';
ALTER TABLE student_ged_subject_score COMMENT = '검정고시 과목별 원점수. 대학별 단위수와 환산등급은 계산 정책에서 적용한다.';
ALTER TABLE student_legacy_grade_summary COMMENT = '과목 성적이 없는 구교육과정의 학기별 계열석차 또는 학년별 석차 요약.';
ALTER TABLE evaluation_rule_legacy_achievement_grade COMMENT = '수·우·미·양·가를 대학 규칙별 석차등급으로 환산하는 표.';

