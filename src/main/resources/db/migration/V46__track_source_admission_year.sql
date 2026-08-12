ALTER TABLE student_transcript_import
    ADD COLUMN source_admission_year INT NULL COMMENT '업로드 원천 파일에 기록된 입학연도' AFTER admission_year;
