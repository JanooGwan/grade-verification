ALTER TABLE student
    ADD COLUMN education_background VARCHAR(40) NOT NULL DEFAULT 'DOMESTIC_HIGH_SCHOOL' AFTER graduation_year,
    ADD COLUMN graduation_status VARCHAR(30) NOT NULL DEFAULT 'EXPECTED_GRADUATE' AFTER education_background,
    ADD COLUMN ged_average_score DECIMAL(5,2) NULL AFTER graduation_status;

UPDATE student
SET graduation_status = CASE
    WHEN graduation_year IS NOT NULL AND graduation_year < admission_year THEN 'GRADUATE'
    ELSE 'EXPECTED_GRADUATE'
END;

CREATE TABLE student_attendance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    school_year INT NOT NULL,
    unexcused_absence_days INT NOT NULL DEFAULT 0,
    unexcused_tardy_count INT NOT NULL DEFAULT 0,
    unexcused_early_leave_count INT NOT NULL DEFAULT 0,
    unexcused_class_absence_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_student_attendance PRIMARY KEY (id),
    CONSTRAINT uk_student_attendance_year UNIQUE (student_id, school_year),
    CONSTRAINT fk_student_attendance_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE
);

CREATE TABLE student_school_violence_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    school_year INT,
    action_number INT NOT NULL,
    action_date DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    note VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_student_school_violence_action PRIMARY KEY (id),
    CONSTRAINT fk_student_school_violence_action_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE
);

CREATE INDEX idx_student_school_violence_action_student_active
    ON student_school_violence_action (student_id, active);
