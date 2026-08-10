ALTER TABLE verification_run
    MODIFY COLUMN source_import_id BIGINT NULL
        COMMENT '일괄 검증 결과가 계산된 학생 성적 업로드 이력 식별자',
    MODIFY COLUMN average_grade DECIMAL(10,6) NULL
        COMMENT '반영 과목의 평균 등급이며 지원자격 미달 등 계산할 수 없는 경우 NULL';
