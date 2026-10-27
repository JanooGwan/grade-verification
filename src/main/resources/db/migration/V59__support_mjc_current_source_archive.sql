CREATE TABLE student_mjc_archive_applicant (
    import_id BIGINT NOT NULL,
    applicant_number VARCHAR(100) NOT NULL,
    source_metadata JSON NOT NULL COMMENT '현재 수신 원자료 연결 ID, 학력 분류, 미산출 사유 및 검정고시 과목. 개인정보 로그 출력 금지',
    verification_code VARCHAR(100) NULL,
    verification_reason VARCHAR(1000) NULL,
    CONSTRAINT pk_mjc_archive_applicant PRIMARY KEY (import_id, applicant_number),
    CONSTRAINT fk_mjc_archive_import FOREIGN KEY (import_id)
        REFERENCES student_transcript_import (id) ON DELETE CASCADE
);

ALTER TABLE student_transcript_import_course
    ADD COLUMN source_applicant_id VARCHAR(100) NULL,
    ADD COLUMN source_student_record_id VARCHAR(100) NULL,
    ADD COLUMN source_subject_id VARCHAR(100) NULL;

INSERT INTO university_import_profile (
    university_id, source_format, name, schema_version, replaces_previous_data,
    column_mapping, active, created_at, updated_at
)
SELECT id, 'MJC_CURRENT_SOURCE_ZIP_V1', '명지전문대 현재 수신 원자료 ZIP', 'v1', TRUE,
       '{"manifest":"file-index.csv","parts":"parts/current-part-*.csv"}', active, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM university WHERE code = 'MJC';
