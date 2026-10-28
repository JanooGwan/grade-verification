package com.jinhakapply.gradevalidation.admission.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository.ApplicationVerificationRow;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowCallbackHandler;

class BatchVerificationRunRepositoryTest {
    @Test
    void writesMultipleValuesInOneStatementWithTheSameResultFieldsAsIndividualInserts() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var connection = mock(Connection.class);
        Map<Integer, Object> parameters = new HashMap<>();
        var statement = mock(PreparedStatement.class, call -> {
            if (call.getMethod().getName().startsWith("set") && call.getArguments().length == 2) {
                parameters.put(call.getArgument(0), call.getArgument(1));
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
        });
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        doAnswer(call -> {
            RowCallbackHandler callback = call.getArgument(1);
            Object[] ids = (Object[]) call.getRawArguments()[2];
            for (Object id : ids) {
                ResultSet row = mock(ResultSet.class);
                when(row.getLong("id")).thenReturn((Long) id);
                when(row.getLong("student_id")).thenReturn((Long) id + 100);
                callback.processRow(row);
            }
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        when(jdbc.update(any(PreparedStatementCreator.class))).thenAnswer(call -> {
            call.<PreparedStatementCreator>getArgument(0).createPreparedStatement(connection);
            return 2;
        });
        var first = result("417.64700", "6.82353");
        var second = result("1000.00000", null);
        String json = "{\"source\":\"SYNTHETIC 한글\",\"score\":\"417.64700\"}";
        assertThat(new BatchVerificationRunRepository(jdbc).insertBatch(9L, List.of(
            new ApplicationVerificationRow(11L, first, json), new ApplicationVerificationRow(12L, second, "{}"))))
            .isEqualTo(2);
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertThat(sql.getValue()).contains("VALUES", "),(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6))")
            .doesNotContain("SELECT", "SYNTHETIC", "417.64700");
        assertThat(parameters).containsEntry(1, 9L).containsEntry(2, 111L).containsEntry(3, 11L)
            .containsEntry(4, 7L).containsEntry(5, 3).containsEntry(6, new BigDecimal("417.64700"))
            .containsEntry(7, new BigDecimal("6.82353")).containsEntry(8, 8).containsEntry(9, 2)
            .containsEntry(10, json).containsEntry(12, 112L).containsEntry(13, 12L)
            .containsEntry(16, new BigDecimal("1000.00000")).containsEntry(17, null).containsEntry(20, "{}");
        verify(jdbc, times(1)).update(any(PreparedStatementCreator.class));
    }

    @Test
    void emptyBatchDoesNothingAndMissingApplicationStopsBeforeWriting() {
        var jdbc = mock(JdbcTemplate.class);
        var repository = new BatchVerificationRunRepository(jdbc);
        assertThat(repository.insertBatch(9L, List.of())).isZero();
        verifyNoInteractions(jdbc);
        assertThatThrownBy(() -> repository.insertBatch(9L,
            List.of(new ApplicationVerificationRow(11L, result("1.00000", null), "{}"))))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("지원정보가 없어");
        verify(jdbc, never()).update(any(PreparedStatementCreator.class));
    }

    private static GradeVerificationResponse result(String score, String average) {
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
