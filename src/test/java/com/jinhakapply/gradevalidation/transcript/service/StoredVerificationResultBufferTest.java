package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository;
import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository.ApplicationVerificationRow;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import org.junit.jupiter.api.Test;

class StoredVerificationResultBufferTest {
    @Test
    void reducesTwentyThousandSmallResultWritesToOneHundredWithoutChangingOrderOrPayload() {
        var repository = mock(BatchVerificationRunRepository.class);
        var result = mock(GradeVerificationResponse.class);
        var batches = new ArrayList<List<ApplicationVerificationRow>>();
        when(repository.insertBatch(eq(1L), anyList())).thenAnswer(call -> {
            List<ApplicationVerificationRow> rows = call.getArgument(1);
            batches.add(List.copyOf(rows));
            return rows.size();
        });
        var progress = new ArrayList<Integer>();
        var buffer = new StoredVerificationResultBuffer(repository, 1L, progress::add);
        for (long id = 1; id <= 20_000; id++) buffer.add(id, result, "{\"synthetic\":" + id + ",\"score\":\"417.64700\"}");
        buffer.flush();
        assertThat(buffer.savedResults()).isEqualTo(20_000);
        assertThat(batches).hasSize(100).allSatisfy(rows -> assertThat(rows).hasSize(200));
        var flattened = batches.stream().flatMap(List::stream).toList();
        for (int index = 0; index < flattened.size(); index++) {
            var row = flattened.get(index);
            assertThat(row.applicationId()).isEqualTo(index + 1L);
            assertThat(row.result()).isSameAs(result);
            assertThat(row.resultJson()).isEqualTo("{\"synthetic\":" + (index + 1) + ",\"score\":\"417.64700\"}");
        }
        assertThat(progress).hasSize(100).endsWith(20_000);
    }

    @Test
    void byteBudgetFlushesBeforeLimitAndKeepsAnOversizedSingleResultIntact() {
        var repository = mock(BatchVerificationRunRepository.class);
        var result = mock(GradeVerificationResponse.class);
        var batches = new ArrayList<List<ApplicationVerificationRow>>();
        when(repository.insertBatch(eq(1L), anyList())).thenAnswer(call -> {
            List<ApplicationVerificationRow> rows = call.getArgument(1);
            batches.add(List.copyOf(rows));
            return rows.size();
        });
        var buffer = new StoredVerificationResultBuffer(repository, 1L, ignored -> { }, 200, 300);
        buffer.add(1L, result, "한글");
        buffer.add(2L, result, "😀");
        buffer.add(3L, result, "상세 결과".repeat(100));
        buffer.add(4L, result, "끝");
        buffer.flush();
        assertThat(batches).extracting(List::size).containsExactly(2, 1, 1);
        assertThat(batches.get(1).getFirst().resultJson()).isEqualTo("상세 결과".repeat(100));
        assertThat(buffer.savedResults()).isEqualTo(4);
    }

    @Test
    void flushesRemainderExactlyOnceAndDoesNotPublishSuccessOnWriteFailure() {
        var repository = mock(BatchVerificationRunRepository.class);
        var result = mock(GradeVerificationResponse.class);
        var progress = new ArrayList<Integer>();
        when(repository.insertBatch(eq(1L), anyList())).thenReturn(1);
        var buffer = new StoredVerificationResultBuffer(repository, 1L, progress::add);
        buffer.add(1L, result, "{}");
        buffer.flush();
        buffer.flush();
        verify(repository, times(1)).insertBatch(eq(1L), anyList());
        assertThat(progress).containsExactly(1);

        var broken = new StoredVerificationResultBuffer(repository, 1L, progress::add, 2, 10_000);
        broken.add(1L, result, "{}");
        assertThatThrownBy(() -> broken.add(2L, result, "{}"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("건수가 일치하지");
        assertThat(broken.savedResults()).isZero();
        assertThat(progress).containsExactly(1);
    }
}
