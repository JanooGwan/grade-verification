CREATE TABLE admission_track (
    id BIGINT NOT NULL AUTO_INCREMENT,
    university_id BIGINT NOT NULL,
    admission_year INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_admission_track PRIMARY KEY (id),
    CONSTRAINT fk_admission_track_university FOREIGN KEY (university_id) REFERENCES university (id),
    CONSTRAINT uk_admission_track UNIQUE (university_id, admission_year, name)
);

CREATE INDEX idx_admission_track_lookup
    ON admission_track (university_id, admission_year, active);

CREATE TABLE recruitment_unit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admission_track_id BIGINT NOT NULL,
    code VARCHAR(30),
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_recruitment_unit PRIMARY KEY (id),
    CONSTRAINT fk_recruitment_unit_track FOREIGN KEY (admission_track_id) REFERENCES admission_track (id),
    CONSTRAINT uk_recruitment_unit_name UNIQUE (admission_track_id, name),
    CONSTRAINT uk_recruitment_unit_code UNIQUE (admission_track_id, code)
);

CREATE INDEX idx_recruitment_unit_track
    ON recruitment_unit (admission_track_id, active);

CREATE TABLE student_application (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    recruitment_unit_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_student_application PRIMARY KEY (id),
    CONSTRAINT fk_student_application_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE,
    CONSTRAINT fk_student_application_unit FOREIGN KEY (recruitment_unit_id) REFERENCES recruitment_unit (id),
    CONSTRAINT uk_student_application UNIQUE (student_id, recruitment_unit_id)
);

CREATE INDEX idx_student_application_student ON student_application (student_id);

-- 기존 규칙에 입력된 문자열을 전형/모집단위 카탈로그의 초기 데이터로 사용한다.
INSERT INTO admission_track (university_id, admission_year, name, active, created_at, updated_at)
SELECT DISTINCT university_id, admission_year, TRIM(admission_type), TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule
WHERE TRIM(admission_type) <> '';

INSERT INTO recruitment_unit (admission_track_id, code, name, active, created_at, updated_at)
SELECT DISTINCT at.id, NULL, TRIM(er.recruitment_unit), TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM evaluation_rule er
JOIN admission_track at
  ON at.university_id = er.university_id
 AND at.admission_year = er.admission_year
 AND at.name = TRIM(er.admission_type)
WHERE TRIM(er.recruitment_unit) <> '';
