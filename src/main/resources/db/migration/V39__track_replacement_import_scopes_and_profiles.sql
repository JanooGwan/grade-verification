-- Every persisted row can now point to the exact import that owns it.  A new
-- import replaces only the previous data for the same university/year/format,
-- so unrelated manual data and other university formats are not deleted.
ALTER TABLE student_transcript_course
    ADD COLUMN source_import_id BIGINT NULL AFTER student_id,
    ADD CONSTRAINT fk_transcript_course_source_import
        FOREIGN KEY (source_import_id) REFERENCES student_transcript_import (id) ON DELETE SET NULL,
    ADD INDEX idx_transcript_course_source_import (source_import_id, student_id);

UPDATE student_transcript_course course
JOIN student student ON student.id = course.student_id
JOIN (
    SELECT university_id, admission_year, original_file_name, MAX(id) AS import_id
    FROM student_transcript_import
    GROUP BY university_id, admission_year, original_file_name
) import_history
  ON import_history.university_id = student.university_id
 AND import_history.admission_year = student.admission_year
 AND import_history.original_file_name = course.source_file_name
SET course.source_import_id = import_history.import_id;

ALTER TABLE student_application
    ADD COLUMN source_import_id BIGINT NULL AFTER student_id,
    ADD CONSTRAINT fk_student_application_source_import
        FOREIGN KEY (source_import_id) REFERENCES student_transcript_import (id) ON DELETE SET NULL,
    ADD INDEX idx_student_application_source_import (source_import_id, student_id);

-- Historical applications were created only from the Hanshin delivery format.
UPDATE student_application application
JOIN student student ON student.id = application.student_id
JOIN recruitment_unit unit ON unit.id = application.recruitment_unit_id
JOIN admission_track track ON track.id = unit.admission_track_id
JOIN (
    SELECT university_id, admission_year, MAX(id) AS import_id
    FROM student_transcript_import
    WHERE source_format = 'HANSHIN_MULTI_SHEET_V1'
    GROUP BY university_id, admission_year
) import_history
  ON import_history.university_id = track.university_id
 AND import_history.admission_year = student.admission_year
SET application.source_import_id = import_history.import_id;

ALTER TABLE student_attendance
    ADD COLUMN source_import_id BIGINT NULL AFTER student_id,
    ADD CONSTRAINT fk_student_attendance_source_import
        FOREIGN KEY (source_import_id) REFERENCES student_transcript_import (id) ON DELETE SET NULL,
    ADD INDEX idx_student_attendance_source_import (source_import_id, student_id);

-- 출결은 현재 삼육대 원천 파일에서만 일괄 수집한다. 기존 행도 해당 파일 이력에 연결해
-- 다음 업로드 때 파일에 사라진 연도 행까지 함께 삭제할 수 있도록 한다.
UPDATE student_attendance attendance
JOIN student student ON student.id = attendance.student_id
JOIN (
    SELECT university_id, admission_year, MAX(id) AS import_id
    FROM student_transcript_import
    WHERE source_format = 'SYU_SOURCE_WORKBOOK_V1'
    GROUP BY university_id, admission_year
) import_history
  ON import_history.university_id = student.university_id
 AND import_history.admission_year = student.admission_year
SET attendance.source_import_id = import_history.import_id;

CREATE TABLE university_import_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    university_id BIGINT NOT NULL,
    source_format VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    schema_version VARCHAR(30) NOT NULL,
    replaces_previous_data BOOLEAN NOT NULL DEFAULT TRUE,
    column_mapping LONGTEXT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_university_import_profile PRIMARY KEY (id),
    CONSTRAINT fk_import_profile_university FOREIGN KEY (university_id) REFERENCES university (id),
    CONSTRAINT uk_import_profile UNIQUE (university_id, source_format, schema_version)
) COMMENT = '대학별 업로드 양식 버전과 헤더 매핑·전체교체 정책을 관리한다.';

CREATE TABLE university_export_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    university_id BIGINT NOT NULL,
    export_format VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    schema_version VARCHAR(30) NOT NULL,
    column_definition LONGTEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_university_export_profile PRIMARY KEY (id),
    CONSTRAINT fk_export_profile_university FOREIGN KEY (university_id) REFERENCES university (id),
    CONSTRAINT uk_export_profile UNIQUE (university_id, export_format, schema_version)
) COMMENT = '대학별 결과 파일의 칼럼 순서·표시명·버전을 관리한다.';

INSERT INTO university_import_profile (
    university_id, source_format, name, schema_version, replaces_previous_data,
    column_mapping, active, created_at, updated_at
)
SELECT id, 'STANDARD_TRANSCRIPT_V1', '표준 학생부 성적양식', 'v1', TRUE,
       NULL, active, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM university
WHERE code <> 'LEGACY';

INSERT INTO university_import_profile (
    university_id, source_format, name, schema_version, replaces_previous_data,
    column_mapping, active, created_at, updated_at
)
SELECT id, 'HANSHIN_MULTI_SHEET_V1', '한신대 전달양식', 'v1', TRUE,
       NULL, active, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM university
WHERE code = 'HS';

INSERT INTO university_import_profile (
    university_id, source_format, name, schema_version, replaces_previous_data,
    column_mapping, active, created_at, updated_at
)
SELECT id, 'SYU_SOURCE_WORKBOOK_V1', '삼육대 원천 대용량 양식', 'v1', TRUE,
       NULL, active, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM university
WHERE code = 'SY';

INSERT INTO university_export_profile (
    university_id, export_format, name, schema_version, column_definition,
    active, created_at, updated_at
)
SELECT id, 'TRANSCRIPT_VALIDATION_XLSX', '표준 성적 검증 결과', 'v1',
       '["수험번호","성명","학년","학기","교과","과목명","성적"]',
       active, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM university
WHERE code <> 'LEGACY';

INSERT INTO university_export_profile (
    university_id, export_format, name, schema_version, column_definition,
    active, created_at, updated_at
)
SELECT id, 'SYU_IMPORT_SCORE_XLSX', '삼육대 반영점수 결과', 'v1',
       '["수험번호","반영과목수","학기별점수","최종교과성적"]',
       active, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM university
WHERE code = 'SY';
