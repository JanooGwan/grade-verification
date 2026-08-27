package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.BiConsumer;

import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SyuScenarioVerificationPersistenceServiceTest {

    @Mock SyuImportScoreExcelWriter scoreExcelWriter;
    @Mock SyuScenarioVerificationBatchWriter batchWriter;
    @Mock ObjectMapper objectMapper;
    @Mock StudentTranscriptImport transcriptImport;
    @Mock GradeVerificationResponse firstResult;
    @Mock GradeVerificationResponse secondResult;
    @Mock EvaluationRule rule;

    private SyuScenarioVerificationPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new SyuScenarioVerificationPersistenceService(
            scoreExcelWriter, batchWriter, objectMapper
        );
    }

    @Test
    void storesScenarioResultsWithStudentIdsAndNoApplications() {
        when(transcriptImport.getId()).thenReturn(80L);
        when(batchWriter.deleteAll(80L)).thenReturn(3);
        when(objectMapper.writeValueAsString(firstResult)).thenReturn("{first}");
        when(objectMapper.writeValueAsString(secondResult)).thenReturn("{second}");
        when(objectMapper.writeValueAsString(any(SyuScenarioExportSummary.class)))
            .thenReturn("{summary}");
        stubExportSummary(firstResult);
        stubExportSummary(secondResult);
        when(batchWriter.insert(eq(80L), anyList())).thenAnswer(invocation ->
            ((List<?>) invocation.getArgument(1)).size()
        );
        doAnswer(invocation -> {
            BiConsumer<Long, GradeVerificationResponse> consumer = invocation.getArgument(2);
            consumer.accept(11L, firstResult);
            consumer.accept(12L, secondResult);
            return new SyuImportScoreExcelWriter.ScenarioVerificationSummary(3, 2, 1);
        }).when(scoreExcelWriter).verifyScenarios(
            eq(transcriptImport), eq(List.of(rule)), org.mockito.ArgumentMatchers.any()
        );

        var response = service.persist(transcriptImport, List.of(rule));

        assertThat(response.sourceImportId()).isEqualTo(80L);
        assertThat(response.totalApplications()).isEqualTo(3);
        assertThat(response.savedResults()).isEqualTo(2);
        assertThat(response.failedResults()).isEqualTo(1);
        assertThat(response.replacedResults()).isEqualTo(3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BatchVerificationRunRepository.ScenarioVerificationRow>> rowsCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(batchWriter).insert(eq(80L), rowsCaptor.capture());
        assertThat(rowsCaptor.getValue())
            .extracting(BatchVerificationRunRepository.ScenarioVerificationRow::studentId)
            .containsExactly(11L, 12L);
        assertThat(rowsCaptor.getValue())
            .extracting(BatchVerificationRunRepository.ScenarioVerificationRow::exportSummaryJson)
            .containsExactly("{summary}", "{summary}");
    }

    @Test
    void leavesRollbackToTheSingleReplacementTransactionWhenVerificationFails() {
        when(transcriptImport.getId()).thenReturn(80L);
        when(batchWriter.deleteAll(80L)).thenReturn(3);
        RuntimeException failure = new RuntimeException("scenario verification failed");
        when(scoreExcelWriter.verifyScenarios(
            eq(transcriptImport), eq(List.of(rule)), org.mockito.ArgumentMatchers.any()
        )).thenThrow(failure);

        assertThatThrownBy(() -> service.persist(transcriptImport, List.of(rule))).isSameAs(failure);

        verify(batchWriter, times(1)).deleteAll(80L);
        verify(batchWriter, never()).insert(eq(80L), anyList());
    }

    private void stubExportSummary(GradeVerificationResponse result) {
        GradeVerificationResponse.CalculationSummary summary =
            org.mockito.Mockito.mock(GradeVerificationResponse.CalculationSummary.class);
        when(summary.convertedScoreTimesCreditsSum()).thenReturn(java.math.BigDecimal.TEN);
        when(summary.totalIncludedCredits()).thenReturn(java.math.BigDecimal.ONE);
        when(result.calculationSummary()).thenReturn(summary);
        when(result.calculations()).thenReturn(List.of());
        when(result.baseScore()).thenReturn(java.math.BigDecimal.TEN);
    }
}
