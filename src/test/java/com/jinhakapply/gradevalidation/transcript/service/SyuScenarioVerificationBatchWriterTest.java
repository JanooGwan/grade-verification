package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class SyuScenarioVerificationBatchWriterTest {

    @Mock BatchVerificationRunRepository repository;

    @Test
    void requiresAnEnclosingTransactionForEachBatch() throws NoSuchMethodException {
        Method method = SyuScenarioVerificationBatchWriter.class.getMethod(
            "insert", Long.class, List.class
        );

        assertThat(method.getAnnotation(Transactional.class).propagation())
            .isEqualTo(Propagation.MANDATORY);
    }

    @Test
    void delegatesBatchInsertAndCleanup() {
        SyuScenarioVerificationBatchWriter writer = new SyuScenarioVerificationBatchWriter(repository);
        when(repository.insertScenarios(80L, List.of())).thenReturn(0);
        when(repository.deleteAllBySourceImportId(80L)).thenReturn(3);

        assertThat(writer.insert(80L, List.of())).isZero();
        assertThat(writer.deleteAll(80L)).isEqualTo(3);

        verify(repository).insertScenarios(80L, List.of());
        verify(repository).deleteAllBySourceImportId(80L);
    }
}
