-- A student number is issued by each university, not globally.  Keep an inactive
-- legacy university for records whose source did not carry enough information to
-- attribute them safely instead of guessing a real university.
INSERT INTO university (code, name, active, created_at, updated_at)
SELECT 'LEGACY', '대학 미확정 기존 데이터', FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM university WHERE code = 'LEGACY');

ALTER TABLE student
    ADD COLUMN university_id BIGINT NULL AFTER id;

ALTER TABLE student_transcript_import
    ADD COLUMN university_id BIGINT NULL AFTER id;

-- The source format itself identifies historical Hanshin and Sahmyook uploads.
UPDATE student_transcript_import import_history
JOIN university university ON university.code = 'SY'
SET import_history.university_id = university.id
WHERE import_history.source_format = 'SYU_SOURCE_WORKBOOK_V1';

UPDATE student_transcript_import import_history
JOIN university university ON university.code = 'HS'
SET import_history.university_id = university.id
WHERE import_history.university_id IS NULL
  AND import_history.source_format = 'HANSHIN_MULTI_SHEET_V1';

UPDATE student_transcript_import import_history
JOIN university university ON university.code = 'LEGACY'
SET import_history.university_id = university.id
WHERE import_history.university_id IS NULL;

-- Reconstruct every known university context.  A student with historical
-- applications at multiple universities is split into one row per university;
-- all of the historical transcript/common data is copied so no data is lost.
CREATE TEMPORARY TABLE tmp_student_university_context (
    student_id BIGINT NOT NULL,
    university_id BIGINT NOT NULL,
    PRIMARY KEY (student_id, university_id)
);

INSERT IGNORE INTO tmp_student_university_context (student_id, university_id)
SELECT application.student_id, track.university_id
FROM student_application application
JOIN recruitment_unit unit ON unit.id = application.recruitment_unit_id
JOIN admission_track track ON track.id = unit.admission_track_id;

INSERT IGNORE INTO tmp_student_university_context (student_id, university_id)
SELECT course.student_id, import_history.university_id
FROM student_transcript_course course
JOIN student student ON student.id = course.student_id
JOIN student_transcript_import import_history
  ON import_history.admission_year = student.admission_year
 AND import_history.original_file_name = course.source_file_name;

INSERT IGNORE INTO tmp_student_university_context (student_id, university_id)
SELECT student.id, university.id
FROM student student
JOIN university university ON university.code = 'LEGACY'
LEFT JOIN tmp_student_university_context context ON context.student_id = student.id
WHERE context.student_id IS NULL;

CREATE TEMPORARY TABLE tmp_student_primary_university AS
SELECT student_id, MIN(university_id) AS university_id
FROM tmp_student_university_context
GROUP BY student_id;

UPDATE student student
JOIN tmp_student_primary_university primary_context ON primary_context.student_id = student.id
SET student.university_id = primary_context.university_id;

-- The old global natural key prevents creating a second university context.
ALTER TABLE student
    DROP INDEX uk_student_admission_applicant;

INSERT INTO student (
    university_id, admission_year, applicant_number, name, high_school_code,
    high_school_name, graduation_year, education_background, high_school_type,
    graduation_status, ged_average_score, created_at, updated_at
)
SELECT context.university_id, student.admission_year, student.applicant_number,
       student.name, student.high_school_code, student.high_school_name,
       student.graduation_year, student.education_background, student.high_school_type,
       student.graduation_status, student.ged_average_score, student.created_at,
       student.updated_at
FROM tmp_student_university_context context
JOIN tmp_student_primary_university primary_context
  ON primary_context.student_id = context.student_id
JOIN student student ON student.id = context.student_id
WHERE context.university_id <> primary_context.university_id;

CREATE TEMPORARY TABLE tmp_student_university_target (
    original_student_id BIGINT NOT NULL,
    university_id BIGINT NOT NULL,
    target_student_id BIGINT NOT NULL,
    PRIMARY KEY (original_student_id, university_id)
);

INSERT INTO tmp_student_university_target (original_student_id, university_id, target_student_id)
SELECT primary_context.student_id, primary_context.university_id, primary_context.student_id
FROM tmp_student_primary_university primary_context;

INSERT INTO tmp_student_university_target (original_student_id, university_id, target_student_id)
SELECT context.student_id, context.university_id, clone.id
FROM tmp_student_university_context context
JOIN tmp_student_primary_university primary_context
  ON primary_context.student_id = context.student_id
JOIN student original_student ON original_student.id = context.student_id
JOIN student clone
  ON clone.university_id = context.university_id
 AND clone.admission_year = original_student.admission_year
 AND clone.applicant_number = original_student.applicant_number
WHERE context.university_id <> primary_context.university_id;

INSERT INTO student_transcript_course (
    student_id, school_year, semester, subject_category, course_name, grade_value,
    grade_scale, achievement, raw_score, mean_score, standard_deviation,
    student_count, rank_position, tied_rank_count, legacy_achievement, credits,
    career_subject, professional_course, source_file_name, source_row_number,
    created_at, updated_at
)
SELECT target.target_student_id, course.school_year, course.semester,
       course.subject_category, course.course_name, course.grade_value,
       course.grade_scale, course.achievement, course.raw_score, course.mean_score,
       course.standard_deviation, course.student_count, course.rank_position,
       course.tied_rank_count, course.legacy_achievement, course.credits,
       course.career_subject, course.professional_course, course.source_file_name,
       course.source_row_number, course.created_at, course.updated_at
FROM tmp_student_university_target target
JOIN student_transcript_course course ON course.student_id = target.original_student_id
WHERE target.target_student_id <> target.original_student_id;

INSERT INTO student_attendance (
    student_id, school_year, unexcused_absence_days, unexcused_tardy_count,
    unexcused_early_leave_count, unexcused_class_absence_count, created_at, updated_at
)
SELECT target.target_student_id, attendance.school_year, attendance.unexcused_absence_days,
       attendance.unexcused_tardy_count, attendance.unexcused_early_leave_count,
       attendance.unexcused_class_absence_count, attendance.created_at, attendance.updated_at
FROM tmp_student_university_target target
JOIN student_attendance attendance ON attendance.student_id = target.original_student_id
WHERE target.target_student_id <> target.original_student_id;

INSERT INTO student_school_violence_action (
    student_id, school_year, action_number, action_date, active, note, created_at, updated_at
)
SELECT target.target_student_id, action.school_year, action.action_number, action.action_date,
       action.active, action.note, action.created_at, action.updated_at
FROM tmp_student_university_target target
JOIN student_school_violence_action action ON action.student_id = target.original_student_id
WHERE target.target_student_id <> target.original_student_id;

INSERT INTO student_ged_subject_score (
    student_id, subject_type, subject_name, score, created_at, updated_at
)
SELECT target.target_student_id, score.subject_type, score.subject_name, score.score,
       score.created_at, score.updated_at
FROM tmp_student_university_target target
JOIN student_ged_subject_score score ON score.student_id = target.original_student_id
WHERE target.target_student_id <> target.original_student_id;

INSERT INTO student_legacy_grade_summary (
    student_id, summary_type, school_year, semester, rank_position, tied_rank_count,
    cohort_size, credits, created_at, updated_at
)
SELECT target.target_student_id, summary.summary_type, summary.school_year,
       summary.semester, summary.rank_position, summary.tied_rank_count,
       summary.cohort_size, summary.credits, summary.created_at, summary.updated_at
FROM tmp_student_university_target target
JOIN student_legacy_grade_summary summary ON summary.student_id = target.original_student_id
WHERE target.target_student_id <> target.original_student_id;

UPDATE student_application application
JOIN recruitment_unit unit ON unit.id = application.recruitment_unit_id
JOIN admission_track track ON track.id = unit.admission_track_id
JOIN tmp_student_university_target target
  ON target.original_student_id = application.student_id
 AND target.university_id = track.university_id
SET application.student_id = target.target_student_id;

ALTER TABLE student
    MODIFY COLUMN university_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_student_university FOREIGN KEY (university_id) REFERENCES university (id),
    ADD CONSTRAINT uk_student_university_admission_applicant
        UNIQUE (university_id, admission_year, applicant_number),
    ADD INDEX idx_student_university_admission (university_id, admission_year);

ALTER TABLE student_transcript_import
    MODIFY COLUMN university_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_transcript_import_university FOREIGN KEY (university_id) REFERENCES university (id),
    ADD INDEX idx_transcript_import_university_admission_source
        (university_id, admission_year, source_format, created_at);

-- Persist a normalized identity separately from the display name.  This makes
-- 사회·문화, 사회문화, and spacing/Unicode variants one course in a semester.
ALTER TABLE student_transcript_course
    ADD COLUMN course_name_normalized VARCHAR(100) NULL AFTER course_name;

UPDATE student_transcript_course
SET course_name_normalized = REGEXP_REPLACE(LOWER(course_name), '[[:space:]·ㆍ・･]+', '');

DELETE duplicate_course
FROM student_transcript_course duplicate_course
JOIN student_transcript_course retained_course
  ON retained_course.student_id = duplicate_course.student_id
 AND retained_course.school_year = duplicate_course.school_year
 AND retained_course.semester = duplicate_course.semester
 AND retained_course.course_name_normalized = duplicate_course.course_name_normalized
 AND retained_course.id > duplicate_course.id;

ALTER TABLE student_transcript_course
    MODIFY COLUMN course_name_normalized VARCHAR(100) NOT NULL,
    DROP INDEX uk_transcript_course_natural,
    ADD CONSTRAINT uk_transcript_course_normalized
        UNIQUE (student_id, school_year, semester, course_name_normalized);
