package com.jinhakapply.gradevalidation.operation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class LightDataStatusRepositoryTest {
    @Test
    void statusQueryAvoidsExpensiveCourseTableAndExactCounts() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<com.jinhakapply.gradevalidation.operation.dto.LightDataStatus>>any()))
            .thenReturn(List.of());
        new LightDataStatusRepository(jdbc).findAll();
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<com.jinhakapply.gradevalidation.operation.dto.LightDataStatus>>any());
        assertThat(sql.getValue().toLowerCase()).doesNotContain("student_transcript_course", "count(")
            .contains("exists", "source_import_id");
    }
}
