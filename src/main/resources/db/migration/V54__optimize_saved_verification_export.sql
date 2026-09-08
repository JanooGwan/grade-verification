ALTER TABLE verification_run
    ADD INDEX idx_verification_run_source_import_id (source_import_id, id);
