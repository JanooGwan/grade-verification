package com.jinhakapply.gradevalidation.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ReadOnlySqlValidatorTest {

    private final ReadOnlySqlValidator validator = new ReadOnlySqlValidator();
    private final Set<String> allowedTables = Set.of("student", "student_application");

    @Test
    void acceptsSelectUsingAllowedTables() {
        Set<String> tables = validator.validate(
            "SELECT s.admission_year, COUNT(*) FROM student s "
                + "JOIN student_application a ON a.student_id = s.id GROUP BY s.admission_year LIMIT 100",
            allowedTables
        );

        assertThat(tables).containsExactlyInAnyOrder("student", "student_application");
    }

    @Test
    void rejectsWildcardProjection() {
        assertThatThrownBy(() -> validator.validate("SELECT * FROM student", allowedTables))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("SELECT s.* FROM student s", allowedTables))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMutatingOrMultipleStatements() {
        assertThatThrownBy(() -> validator.validate("DELETE FROM student", allowedTables))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("SELECT * FROM student; DROP TABLE student", allowedTables))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("SELECT * FROM student FOR UPDATE", allowedTables))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownAndSystemTables() {
        assertThatThrownBy(() -> validator.validate("SELECT * FROM users", allowedTables))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(
            "SELECT * FROM information_schema.tables", allowedTables
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(
            "SELECT * FROM student s, users u", allowedTables
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDangerousSelectFunctions() {
        assertThatThrownBy(() -> validator.validate("SELECT SLEEP(10) FROM student", allowedTables))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("SELECT * INTO @result FROM student", allowedTables))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("SELECT password FROM student", allowedTables))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
