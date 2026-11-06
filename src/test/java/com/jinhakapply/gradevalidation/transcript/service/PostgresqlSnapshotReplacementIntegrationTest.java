package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
class PostgresqlSnapshotReplacementIntegrationTest {
    @Test
    void removesOnlyTheSelectedUniversityAndYear() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("POSTGRES_TEST_URL"),
                System.getenv("POSTGRES_TEST_USER"), System.getenv("POSTGRES_TEST_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                jdbc.execute("CREATE TEMP TABLE student (id bigint, university_id bigint, admission_year integer)");
                for (String table : List.of("verification_run", "student_application", "student_transcript_course", "student_attendance")) {
                    jdbc.execute("CREATE TEMP TABLE " + table + " (student_id bigint)");
                    jdbc.update("INSERT INTO " + table + " (student_id) VALUES (1), (2), (3)");
                }
                jdbc.update("INSERT INTO student VALUES (1, 4, 2027), (2, 4, 2026), (3, 5, 2027)");
                var students = mock(StudentRepository.class);
                when(students.findAllByUniversity_IdAndAdmissionYear(4L, 2027)).thenReturn(List.of());
                var service = new TranscriptSnapshotReplacementService(students, jdbc);

                var result = service.clear(4L, 2027, true);
                assertThat(result.deletedVerifications()).isEqualTo(1);
                assertThat(result.deletedApplications()).isEqualTo(1);
                assertThat(result.deletedCourses()).isEqualTo(1);
                assertThat(result.deletedAttendance()).isEqualTo(1);
                for (String table : List.of("verification_run", "student_application", "student_transcript_course", "student_attendance")) {
                    assertThat(jdbc.queryForList("SELECT student_id FROM " + table + " ORDER BY student_id", Long.class))
                        .containsExactly(2L, 3L);
                }
            } finally {
                connection.rollback();
            }
        }
    }
}
