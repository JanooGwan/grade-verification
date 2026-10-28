package com.jinhakapply.gradevalidation.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository;
import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository.ApplicationVerificationRow;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Isolated disposable database only; never uses application datasource configuration. */
@Testcontainers(disabledWithoutDocker = true)
class VerificationBatchWriteIntegrationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
        .withDatabaseName("synthetic_batch_check").withUsername("synthetic")
        .withPassword(UUID.randomUUID().toString());
    private JdbcTemplate jdbc;
    private BatchVerificationRunRepository repository;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        var datasource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(datasource);
        repository = new BatchVerificationRunRepository(jdbc);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        jdbc.execute("DROP TABLE IF EXISTS verification_run");
        jdbc.execute("DROP TABLE IF EXISTS student_application");
        jdbc.execute("CREATE TABLE student_application (id BIGINT PRIMARY KEY, student_id BIGINT NOT NULL) ENGINE=InnoDB");
        jdbc.execute("""
            CREATE TABLE verification_run (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, source_import_id BIGINT, student_id BIGINT,
                application_id BIGINT, rule_id BIGINT, rule_version INT, final_score DECIMAL(20,5),
                average_grade DECIMAL(20,5), included_course_count INT, excluded_course_count INT,
                result_json JSON, created_at DATETIME(6),
                UNIQUE KEY uq_result (source_import_id, application_id, rule_id),
                FOREIGN KEY (application_id) REFERENCES student_application(id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        jdbc.update("INSERT INTO student_application VALUES (11,111),(12,112)");
    }

    @Test
    void multiRowValuesPreservesAllScoreFieldsAndDetailedJsonFromTheOldInsertPath() {
        var first = result("417.64700", "6.82353");
        var second = result("1000.00000", null);
        String json = "{\"synthetic\":\"한글\",\"details\":[{\"score\":\"417.64700\",\"included\":false}]}";
        repository.insert(1L, 11L, first, json);
        repository.insert(1L, 12L, second, "{}");
        transaction.executeWithoutResult(status -> repository.insertBatch(2L, List.of(
            new ApplicationVerificationRow(11L, first, json), new ApplicationVerificationRow(12L, second, "{}"))));
        String select = """
            SELECT student_id, application_id, rule_id, rule_version, final_score, average_grade,
                included_course_count, excluded_course_count, CAST(result_json AS CHAR) AS json_text
            FROM verification_run WHERE source_import_id=? ORDER BY application_id
            """;
        assertThat(jdbc.queryForList(select, 2L)).isEqualTo(jdbc.queryForList(select, 1L));
    }

    @Test
    void failedBatchRollsBackReplacementAndKeepsThePreviouslySavedResult() {
        var result = result("417.64700", "6.82353");
        repository.insert(1L, 11L, result, "{\"synthetic_previous\":true}");
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            repository.deleteAllBySourceImportId(1L);
            repository.insertBatch(1L, List.of(new ApplicationVerificationRow(11L, result, "{}"),
                new ApplicationVerificationRow(11L, result, "{}")));
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_run WHERE source_import_id=1", Integer.class))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT result_json FROM verification_run WHERE source_import_id=1", String.class))
            .contains("synthetic_previous");
    }

    private GradeVerificationResponse result(String score, String average) {
        var result = mock(GradeVerificationResponse.class);
        when(result.ruleId()).thenReturn(7L);
        when(result.ruleVersion()).thenReturn(3);
        when(result.finalScore()).thenReturn(new BigDecimal(score));
        when(result.averageGrade()).thenReturn(average == null ? null : new BigDecimal(average));
        when(result.includedCourseCount()).thenReturn(8);
        when(result.excludedCourseCount()).thenReturn(2);
        return result;
    }
}
