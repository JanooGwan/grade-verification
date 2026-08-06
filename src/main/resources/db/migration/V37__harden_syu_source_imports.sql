ALTER TABLE student_transcript_import
    ADD COLUMN temporary_file_path VARCHAR(1024) NULL AFTER original_file_name;

CREATE TABLE student_transcript_import_course (
    import_id BIGINT NOT NULL,
    source_row_number INT NOT NULL,
    applicant_number VARCHAR(50) NOT NULL,
    school_year INT NOT NULL,
    semester INT NOT NULL,
    subject_category VARCHAR(20) NOT NULL,
    course_name VARCHAR(100) NOT NULL,
    grade_value INT NULL,
    grade_scale VARCHAR(20) NOT NULL,
    achievement VARCHAR(10) NULL,
    raw_score DECIMAL(8,4) NULL,
    mean_score DECIMAL(8,4) NULL,
    standard_deviation DECIMAL(8,4) NULL,
    student_count INT NULL,
    rank_position INT NULL,
    tied_rank_count INT NULL,
    legacy_achievement VARCHAR(10) NULL,
    credits DECIMAL(6,2) NOT NULL,
    career_subject BOOLEAN NOT NULL DEFAULT FALSE,
    professional_course BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_student_transcript_import_course PRIMARY KEY (import_id, source_row_number),
    CONSTRAINT fk_transcript_import_course_import FOREIGN KEY (import_id)
        REFERENCES student_transcript_import (id) ON DELETE CASCADE,
    CONSTRAINT ck_transcript_import_course_school_year CHECK (school_year BETWEEN 1 AND 3),
    CONSTRAINT ck_transcript_import_course_semester CHECK (semester BETWEEN 1 AND 2)
);

CREATE INDEX idx_transcript_import_course_applicant
    ON student_transcript_import_course (import_id, applicant_number, source_row_number);

-- Existing source imports predate immutable snapshots. Preserve the latest completed
-- result for each admission-year/file-name pair without pretending older duplicate
-- names can be reconstructed unambiguously.
INSERT INTO student_transcript_import_course (
    import_id, source_row_number, applicant_number, school_year, semester,
    subject_category, course_name, grade_value, grade_scale, achievement,
    raw_score, mean_score, standard_deviation, student_count, rank_position,
    tied_rank_count, legacy_achievement, credits, career_subject,
    professional_course, created_at
)
SELECT latest.id, course.source_row_number, student.applicant_number,
       course.school_year, course.semester, course.subject_category, course.course_name,
       course.grade_value, course.grade_scale, course.achievement, course.raw_score,
       course.mean_score, course.standard_deviation, course.student_count,
       course.rank_position, course.tied_rank_count, course.legacy_achievement,
       course.credits, course.career_subject, course.professional_course,
       CURRENT_TIMESTAMP(6)
FROM student_transcript_course course
JOIN student ON student.id = course.student_id
JOIN (
    SELECT MAX(id) AS id, admission_year, original_file_name
    FROM student_transcript_import
    WHERE source_format = 'SYU_SOURCE_WORKBOOK_V1'
      AND status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS')
    GROUP BY admission_year, original_file_name
) latest
  ON latest.admission_year = student.admission_year
 AND latest.original_file_name = course.source_file_name;

-- V31 matched only concrete rule/unit names. A published common rule also activates
-- every concrete unit belonging to the same admission track.
UPDATE recruitment_unit unit
JOIN admission_track track ON track.id = unit.admission_track_id
LEFT JOIN evaluation_rule rule
  ON rule.university_id = track.university_id
 AND rule.admission_year = track.admission_year
 AND rule.admission_type = track.name
 AND rule.status = 'PUBLISHED'
 AND (
      rule.recruitment_unit = unit.name
      OR REPLACE(rule.recruitment_unit, ' ', '') IN (
          '전체', '전체모집단위', '전모집단위', '모든모집단위',
          '전체모집학과', '전체학과', '전학과', '공통'
      )
 )
SET unit.active = (rule.id IS NOT NULL),
    unit.updated_at = CURRENT_TIMESTAMP(6)
WHERE (track.admission_year = 2027 AND track.university_id IN (
        SELECT id FROM university WHERE code IN ('TUK', 'MJC', 'SY')
    ))
   OR (track.admission_year = 2026 AND track.university_id IN (
        SELECT id FROM university WHERE code = 'KBOK'
    ));
