package com.jinhakapply.gradevalidation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Uses synthetic data and rolls back; enabled only for an explicitly configured test connection. */
@SpringBootTest
@ActiveProfiles("postgresql")
@Transactional
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
class PostgresqlRepositoryIntegrationTest {
    @Autowired UniversityRepository universities;

    @Test
    void savesAndLoadsUniversityUsingPostgresql() {
        String code = "PGTEST" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        University saved = universities.saveAndFlush(University.create(code, "테스트 대학"));
        assertThat(universities.findById(saved.getId())).get().extracting(University::getCode).isEqualTo(code);
    }
}
