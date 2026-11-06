package com.jinhakapply.gradevalidation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinhakapply.gradevalidation.global.config.DatabaseDialect;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Runs only when an explicitly supplied PostgreSQL connection is available. */
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
class PostgresqlDialectIntegrationTest {
    @Test
    void upsertAndJsonLookupWorkWithPostgresql() throws Exception {
        try (var connection = DriverManager.getConnection(
                System.getenv("POSTGRES_TEST_URL"), System.getenv("POSTGRES_TEST_USER"),
                System.getenv("POSTGRES_TEST_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                jdbc.execute("CREATE TEMP TABLE codex_upsert_test (id integer PRIMARY KEY, name text, active boolean)");
                String mysql = """
                    INSERT INTO codex_upsert_test (id, name, active) VALUES (?, ?, TRUE)
                    ON DUPLICATE KEY UPDATE name=VALUES(name), active=TRUE
                    """;
                String upsert = DatabaseDialect.upsert(jdbc, mysql, "id");
                jdbc.update(upsert, 1, "first");
                jdbc.update(upsert, 1, "second");
                assertThat(jdbc.queryForObject("SELECT name FROM codex_upsert_test WHERE id=1", String.class))
                    .isEqualTo("second");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM codex_upsert_test", Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                    "SELECT CAST(? AS jsonb) -> 'subjectDomains' IS NOT NULL", Boolean.class,
                    "{\"subjectDomains\":[]}")) .isTrue();
            } finally {
                connection.rollback();
            }
        }
    }
}
