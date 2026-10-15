-- 비수치 원천 석차등급은 계산용 숫자와 분리하여 보존한다. 기존 원천값은 재업로드로 복원한다.
ALTER TABLE student_transcript_course ADD COLUMN source_grade_text VARCHAR(50) NULL COMMENT '원천 석차등급 문자열: P·이수 등 비수치 값 포함, 계산에는 grade_value 사용';
ALTER TABLE student_transcript_import_course ADD COLUMN source_grade_text VARCHAR(50) NULL COMMENT '가져오기 당시 원천 석차등급 문자열, 비수치 값 포함';
