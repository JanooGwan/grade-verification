package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository;
import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository.ApplicationVerificationRow;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.dto.StoredVerificationProgress;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

class StoredVerificationPersistenceBatchTest {
    @Test
    void existingCalculatorCallbackFeedsBatchesAndFinalCountsIncludeUnscoredApplicants() {
        Fixture fixture = fixture();
        var batches = new ArrayList<List<ApplicationVerificationRow>>();
        when(fixture.repository.insertBatch(eq(41L), anyList())).thenAnswer(call -> {
            List<ApplicationVerificationRow> rows = call.getArgument(1);
            batches.add(List.copyOf(rows));
            return rows.size();
        });
        var progress = new ArrayList<StoredVerificationProgress>();
        var response = fixture.service.persist(1L, 2027, progress::add);
        assertThat(response.totalApplications()).isEqualTo(501);
        assertThat(response.savedResults()).isEqualTo(500);
        assertThat(response.failedResults()).isEqualTo(1);
        assertThat(response.replacedResults()).isEqualTo(10);
        assertThat(batches).extracting(List::size).containsExactly(200, 200, 100);
        assertThat(batches.stream().flatMap(List::stream)).allSatisfy(row -> {
            assertThat(row.result()).isSameAs(fixture.result);
            assertThat(row.resultJson()).isEqualTo("{\"synthetic_score\":\"417.64700\",\"details\":[]}");
        });
        assertThat(progress.stream().filter(item -> item.stage().equals("VERIFYING_AND_SAVING"))
            .map(StoredVerificationProgress::savedResults).toList()).containsExactly(0, 200, 400, 500);
        assertThat(progress.getLast()).isEqualTo(new StoredVerificationProgress("COMMITTING", 501, 500, 1));
        verify(fixture.repository, never()).insert(any(), any(), any(), anyString());
    }

    @Test
    void writeErrorsEscapeTheTransactionalMethodInsteadOfReportingPartialSuccess() {
        Fixture fixture = fixture();
        var failure = new org.springframework.dao.DataAccessResourceFailureException("synthetic write failure");
        when(fixture.repository.insertBatch(eq(41L), anyList())).thenThrow(failure);
        var progress = new ArrayList<StoredVerificationProgress>();
        assertThatThrownBy(() -> fixture.service.persist(1L, 2027, progress::add)).isSameAs(failure);
        assertThat(progress).noneMatch(item -> item.savedResults() > 0 || item.stage().equals("COMMITTING"));
    }

    private Fixture fixture() {
        var applications = new ArrayList<TransferApplicationRow>();
        for (long id = 1; id <= 501; id++) applications.add(new TransferApplicationRow(id, (int) id, 2027,
            "SYNTHETIC-" + id, "0105", "특별[일반고]", "UNIT", "합성학과", 2027, null));
        var jdbc = mock(JdbcTemplate.class, call -> {
            if (call.getMethod().getName().equals("query") && call.getArguments().length > 1
                && call.getArgument(1) instanceof RowMapper<?>) {
                String sql = call.getArgument(0);
                if (sql.contains("FROM student_application application")) return applications;
                if (sql.contains("FROM student_transcript_course course")) return List.of(mock(TranscriptExcelRow.class));
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
        });
        var imports = mock(StudentTranscriptImportRepository.class);
        var transcriptImport = mock(StudentTranscriptImport.class);
        when(transcriptImport.getId()).thenReturn(41L);
        when(transcriptImport.getSourceFormat()).thenReturn("SYNTHETIC_SOURCE");
        when(imports.findTopByUniversity_IdAndAdmissionYearAndStatusInOrderByCreatedAtDesc(eq(1L), eq(2027), anyList()))
            .thenReturn(Optional.of(transcriptImport));
        var repository = mock(BatchVerificationRunRepository.class);
        when(repository.deleteAllBySourceImportId(41L)).thenReturn(10);
        var calculator = mock(TranscriptBatchVerificationService.class);
        var result = mock(GradeVerificationResponse.class);
        when(calculator.verifyForPersistence(eq(1L), eq(2027), anyList(), anyList(), anyMap(), anyMap(), any()))
            .thenAnswer(call -> {
                BiConsumer<TransferApplicationRow, GradeVerificationResponse> consumer = call.getArgument(6);
                var successes = new ArrayList<TranscriptBatchVerificationResult.Success>();
                for (var application : applications.subList(0, 500)) {
                    consumer.accept(application, result);
                    successes.add(new TranscriptBatchVerificationResult.Success(application, "合成", result,
                        List.of(), List.of(), null));
                }
                return new TranscriptBatchVerificationResult(successes, List.of(new TranscriptBatchVerificationResult.Failure(
                    applications.getLast(), "合成", 0, "COURSE_NOT_FOUND", "합성 입력 누락")));
            });
        var mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(result)).thenReturn("{\"synthetic_score\":\"417.64700\",\"details\":[]}");
        var service = new StoredTranscriptVerificationService(imports, jdbc, calculator,
            mock(TranscriptValidationExcelWriter.class), mock(SyuImportScoreExcelWriter.class),
            mock(SyuScenarioVerificationPersistenceService.class), repository, mapper);
        return new Fixture(service, repository, result);
    }

    private record Fixture(StoredTranscriptVerificationService service, BatchVerificationRunRepository repository,
        GradeVerificationResponse result) {}
}
