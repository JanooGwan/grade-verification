ALTER TABLE evaluation_rule
    ADD COLUMN selection_policy LONGTEXT NULL AFTER selection_strategy;
