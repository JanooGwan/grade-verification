ALTER TABLE student
    ADD COLUMN applicant_high_school_category_code VARCHAR(50) NULL
        COMMENT '지원자 추가정보 파일의 지원자 고교구분코드'
        AFTER high_school_type;
