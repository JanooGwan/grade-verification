-- Keep the schema self-describing for operations and the read-only assistant.
-- MySQL requires a full column definition to add a comment, so this migration
-- preserves every existing definition while documenting only blank comments.
DELIMITER $$

CREATE PROCEDURE document_missing_application_column_comments()
BEGIN
    DECLARE finished BOOLEAN DEFAULT FALSE;
    DECLARE table_name_value VARCHAR(64);
    DECLARE column_name_value VARCHAR(64);
    DECLARE column_type_value LONGTEXT;
    DECLARE nullable_value VARCHAR(3);
    DECLARE default_value LONGTEXT;
    DECLARE extra_value LONGTEXT;
    DECLARE charset_value VARCHAR(64);
    DECLARE collation_value VARCHAR(64);
    DECLARE generated_expression_value LONGTEXT;
    DECLARE column_cursor CURSOR FOR
        SELECT column_name, table_name, column_type, is_nullable, column_default,
               extra, character_set_name, collation_name, generation_expression
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name <> 'flyway_schema_history'
          AND column_comment = ''
        ORDER BY table_name, ordinal_position;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = TRUE;

    OPEN column_cursor;
    comment_loop: LOOP
        FETCH column_cursor INTO column_name_value, table_name_value, column_type_value,
            nullable_value, default_value, extra_value, charset_value, collation_value,
            generated_expression_value;
        IF finished THEN
            LEAVE comment_loop;
        END IF;

        IF generated_expression_value IS NOT NULL AND generated_expression_value <> '' THEN
            SET @column_definition = CONCAT(
                '`', REPLACE(column_name_value, '`', '``'), '` ', column_type_value,
                ' GENERATED ALWAYS AS (', generated_expression_value, ') ',
                IF(LOWER(extra_value) LIKE '%stored%', 'STORED', 'VIRTUAL')
            );
        ELSE
            SET @column_definition = CONCAT(
                '`', REPLACE(column_name_value, '`', '``'), '` ', column_type_value,
                IF(charset_value IS NULL, '', CONCAT(' CHARACTER SET ', charset_value)),
                IF(collation_value IS NULL, '', CONCAT(' COLLATE ', collation_value)),
                IF(nullable_value = 'NO', ' NOT NULL', ' NULL'),
                CASE
                    WHEN default_value IS NULL THEN ''
                    WHEN UPPER(default_value) LIKE 'CURRENT_TIMESTAMP%' THEN CONCAT(' DEFAULT ', default_value)
                    ELSE CONCAT(' DEFAULT ', QUOTE(default_value))
                END,
                IF(LOWER(extra_value) LIKE '%auto_increment%', ' AUTO_INCREMENT', ''),
                IF(LOWER(extra_value) LIKE '%on update current_timestamp%',
                    CONCAT(' ON UPDATE ', SUBSTRING(extra_value, LOCATE('on update ', LOWER(extra_value)) + 10)),
                    ''
                )
            );
        END IF;

        SET @column_comment = CONCAT(table_name_value, '.', column_name_value, ' 컬럼');
        SET @statement = CONCAT(
            'ALTER TABLE `', REPLACE(table_name_value, '`', '``'), '` MODIFY COLUMN ',
            @column_definition, ' COMMENT ', QUOTE(@column_comment)
        );
        PREPARE document_column_statement FROM @statement;
        EXECUTE document_column_statement;
        DEALLOCATE PREPARE document_column_statement;
    END LOOP;
    CLOSE column_cursor;
END$$

DELIMITER ;

CALL document_missing_application_column_comments();
DROP PROCEDURE document_missing_application_column_comments;
