CREATE TABLE student (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admission_year INT NOT NULL,
    applicant_number VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    high_school_code VARCHAR(30),
    high_school_name VARCHAR(150),
    graduation_year INT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_student PRIMARY KEY (id),
    CONSTRAINT uk_student_admission_applicant UNIQUE (admission_year, applicant_number)
);

CREATE INDEX idx_student_name ON student (name);

CREATE TABLE student_transcript_course (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    school_year INT NOT NULL,
    semester INT NOT NULL,
    subject_category VARCHAR(20) NOT NULL,
    course_name VARCHAR(100) NOT NULL,
    grade_value INT,
    achievement VARCHAR(10),
    raw_score DECIMAL(8,4),
    mean_score DECIMAL(8,4),
    standard_deviation DECIMAL(8,4),
    student_count INT,
    credits DECIMAL(6,2) NOT NULL,
    career_subject BOOLEAN NOT NULL DEFAULT FALSE,
    professional_course BOOLEAN NOT NULL DEFAULT FALSE,
    source_file_name VARCHAR(255) NOT NULL,
    source_row_number INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_student_transcript_course PRIMARY KEY (id),
    CONSTRAINT fk_transcript_course_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE,
    CONSTRAINT uk_transcript_course_natural UNIQUE (
        student_id, school_year, semester, subject_category, course_name
    ),
    CONSTRAINT ck_transcript_school_year CHECK (school_year BETWEEN 1 AND 3),
    CONSTRAINT ck_transcript_semester CHECK (semester BETWEEN 1 AND 2),
    CONSTRAINT ck_transcript_grade CHECK (grade_value IS NULL OR grade_value BETWEEN 1 AND 9)
);

CREATE INDEX idx_transcript_course_student ON student_transcript_course (student_id);

CREATE TABLE student_transcript_import (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admission_year INT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    total_rows INT NOT NULL,
    imported_rows INT NOT NULL,
    failed_rows INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_student_transcript_import PRIMARY KEY (id)
);
