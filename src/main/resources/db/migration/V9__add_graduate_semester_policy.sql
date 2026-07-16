ALTER TABLE evaluation_rule
    ADD COLUMN include_third_year_second_semester_for_graduates BOOLEAN NOT NULL DEFAULT FALSE
    AFTER include_third_year_second_semester;
