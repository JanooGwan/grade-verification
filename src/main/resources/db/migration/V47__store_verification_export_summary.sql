ALTER TABLE verification_run
    ADD COLUMN export_summary_json LONGTEXT NULL
        COMMENT '대용량 Excel 내보내기에 필요한 중간 계산값만 보존한 요약 JSON이다.'
        AFTER result_json;
