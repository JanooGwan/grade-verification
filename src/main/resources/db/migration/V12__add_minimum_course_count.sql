ALTER TABLE evaluation_rule
    ADD COLUMN minimum_course_count INT NOT NULL DEFAULT 0
    AFTER achievement_selection_count;
