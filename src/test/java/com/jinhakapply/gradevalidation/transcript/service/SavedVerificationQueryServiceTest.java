package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationBatchResponse;
import com.jinhakapply.gradevalidation.transcript.repository.SavedVerificationQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SavedVerificationQueryServiceTest {
    @Mock SavedVerificationQueryRepository repository;
    @Mock ObjectMapper objectMapper;
    @Mock GradeVerificationResponse verification;
    @Mock EvaluationRuleRepository ruleRepository;
    @Mock TranscriptBatchVerificationService batchVerificationService;
    @Mock TranscriptValidationExcelWriter validationExcelWriter;
    @Mock SyuSavedVerificationExcelWriter syuSavedVerificationExcelWriter;
    @Mock EvaluationRule rule;

    private SavedVerificationQueryService service;

    @BeforeEach
    void setUp() {
        service = new SavedVerificationQueryService(
            repository, objectMapper, ruleRepository, batchVerificationService, validationExcelWriter,
            syuSavedVerificationExcelWriter
        );
    }

    @Test
    void pagesSavedResultsWithoutLoadingResultJson() {
        when(repository.countResults(80L, "홍길동")).thenReturn(101L);
        when(repository.findResults(80L, "홍길동", 50, 50L)).thenReturn(List.of());

        var response = service.findResults(80L, "  홍길동  ", 1, 50);

        assertThat(response.totalElements()).isEqualTo(101);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isFalse();
        verify(repository).findResults(80L, "홍길동", 50, 50L);
    }

    @Test
    void readsStoredJsonOnlyForSelectedResult() {
        LocalDateTime savedAt = LocalDateTime.of(2027, 1, 2, 3, 4);
        when(repository.findDetail(91L)).thenReturn(java.util.Optional.of(
            new SavedVerificationQueryRepository.DetailProjection(
                91L, 80L, 10L, "2106011001", "홍길동", savedAt, "{stored-result}"
            )
        ));
        when(objectMapper.readValue("{stored-result}", GradeVerificationResponse.class)).thenReturn(verification);

        var response = service.findDetail(91L);

        assertThat(response.verificationRunId()).isEqualTo(91L);
        assertThat(response.sourceImportId()).isEqualTo(80L);
        assertThat(response.verification()).isSameAs(verification);
    }

    @Test
    void exportsSavedBatchJsonWithoutRecalculating() throws Exception {
        LocalDateTime savedAt = LocalDateTime.of(2027, 1, 2, 3, 4);
        when(repository.findBatch(80L)).thenReturn(java.util.Optional.of(
            new SavedVerificationBatchResponse(
                80L, 5L, "경복대학교", 2026, "경복대.xlsx", "KOREAN_MULTI_SHEET_V1", 1, savedAt
            )
        ));
        when(repository.findExportResultsAfter(80L, 0L, 200)).thenReturn(List.of(
            new SavedVerificationQueryRepository.ExportProjection(
                91L, 31L, null, "2B0004", "미등록", "수시 일반고", "101", "(주)간호학과",
                "{stored-result}"
            )
        ));
        when(objectMapper.readValue("{stored-result}", GradeVerificationResponse.class)).thenReturn(verification);
        when(verification.calculations()).thenReturn(List.of());
        when(ruleRepository.findOneById(31L)).thenReturn(java.util.Optional.of(rule));
        when(batchVerificationService.buildIntermediateCalculations(rule, verification)).thenReturn(List.of());
        doAnswer(invocation -> {
            java.io.OutputStream output = invocation.getArgument(10);
            output.write(new byte[] {1, 2, 3});
            return null;
        }).when(validationExcelWriter).write(
            anyString(), anyString(), anyString(), anyInt(), anyInt(),
            anyList(), anyList(), anyList(), anyList(), any(), any()
        );

        byte[] result = service.export(80L);

        assertThat(result).containsExactly(1, 2, 3);
        verify(objectMapper).readValue("{stored-result}", GradeVerificationResponse.class);
        verify(batchVerificationService).buildIntermediateCalculations(rule, verification);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void kbuIncludesUnscoredApplicantsOnlyWhenTheImportStillMatches(boolean sameImport) throws Exception {
        when(repository.findBatch(80L)).thenReturn(java.util.Optional.of(new SavedVerificationBatchResponse(
            80L, 5L, "경복대학교", 2026, "synthetic.xlsx", "KOREAN_MULTI_SHEET_V1", 1, LocalDateTime.now())));
        when(repository.findExportResultsAfter(80L, 0L, 200)).thenReturn(List.of(
            new SavedVerificationQueryRepository.ExportProjection(91L, 31L, 4L, "TEST-4", "테스트",
                "수시 일반고", "TEST", "일반학과", "{stored-result}")));
        when(objectMapper.readValue("{stored-result}", GradeVerificationResponse.class)).thenReturn(verification);
        when(verification.calculations()).thenReturn(List.of());
        when(ruleRepository.findOneById(31L)).thenReturn(java.util.Optional.of(rule));
        when(batchVerificationService.buildIntermediateCalculations(rule, verification)).thenReturn(List.of());
        when(repository.findExportApplications(80L)).thenReturn(List.of(
            TukUnscoredExportTest.applicant(sameImport ? 4 : 5, "수시 일반고", "DOMESTIC_HIGH_SCHOOL",
                java.time.LocalDate.of(2026, 2, 1), 10),
            TukUnscoredExportTest.applicant(2, "수시 일반고", "DOMESTIC_HIGH_SCHOOL",
                java.time.LocalDate.of(2026, 2, 1), 10)));
        doAnswer(invocation -> {
            TranscriptBatchVerificationResult result = invocation.getArgument(9);
            assertThat(result.successes()).hasSize(1);
            assertThat(result.applicationScopeComplete()).isEqualTo(sameImport);
            assertThat(result.failures()).hasSize(sameImport ? 1 : 0);
            if (sameImport) {
                assertThat(result.failures().getFirst().code()).isEqualTo("UNSCORED_REASON_UNAVAILABLE");
                assertThat(result.failures().getFirst().application().applicantNumber()).isEqualTo("TEST-2");
            }
            assertThat((Integer) invocation.getArgument(3)).isEqualTo(sameImport ? 2 : 1);
            return null;
        }).when(validationExcelWriter).write(anyString(), anyString(), anyString(), anyInt(), anyInt(),
            anyList(), anyList(), anyList(), anyList(), any(), any());
        service.export(80L);
        verify(batchVerificationService).buildIntermediateCalculations(rule, verification);
        org.mockito.Mockito.verifyNoMoreInteractions(batchVerificationService);
    }

    @Test
    void streamsSyuSavedResultsWithoutLoadingResultJson() {
        LocalDateTime savedAt = LocalDateTime.of(2027, 1, 2, 3, 4);
        SavedVerificationBatchResponse batch = new SavedVerificationBatchResponse(
            80L, 2L, "삼육대학교", 2027, "삼육대.xlsx",
            SyuSourceExcelStreamer.SOURCE_FORMAT, 184_200, savedAt
        );
        when(repository.findBatch(80L)).thenReturn(java.util.Optional.of(batch));
        when(syuSavedVerificationExcelWriter.write(batch)).thenReturn(new byte[] {4, 5, 6});

        assertThat(service.export(80L)).containsExactly(4, 5, 6);

        verify(syuSavedVerificationExcelWriter).write(batch);
        verifyNoInteractions(objectMapper, ruleRepository, batchVerificationService, validationExcelWriter);
    }

    @Test
    void includesUnscoredApplicantsFromTheSameImportWithoutRecalculatingScores() throws Exception {
        when(repository.findBatch(80L)).thenReturn(java.util.Optional.of(new SavedVerificationBatchResponse(
            80L, 5L, "한국공학대학교", 2026, "synthetic.xlsx", "TUK", 1, LocalDateTime.now())));
        when(repository.findExportResultsAfter(80L, 0L, 200)).thenReturn(List.of(
            new SavedVerificationQueryRepository.ExportProjection(91L, 31L, 4L, "TEST-4", "테스트",
                "지역균형", "TEST", "테스트학과", "{stored-result}")));
        when(objectMapper.readValue("{stored-result}", GradeVerificationResponse.class)).thenReturn(verification);
        when(verification.calculations()).thenReturn(List.of());
        when(ruleRepository.findOneById(31L)).thenReturn(java.util.Optional.of(rule));
        when(batchVerificationService.buildIntermediateCalculations(rule, verification)).thenReturn(List.of());
        when(repository.findExportApplications(80L)).thenReturn(List.of(
            TukUnscoredExportTest.applicant(4, "지역균형", "DOMESTIC_HIGH_SCHOOL", java.time.LocalDate.of(2026, 2, 1), 10),
            TukUnscoredExportTest.applicant(2, "논술", "DOMESTIC_HIGH_SCHOOL", java.time.LocalDate.of(2024, 2, 1), 10),
            TukUnscoredExportTest.applicant(3, "논술", "GED", java.time.LocalDate.of(2025, 1, 1), 0),
            TukUnscoredExportTest.applicant(1, "교과우수자", "GED", java.time.LocalDate.of(2025, 1, 1), 0)));
        doAnswer(invocation -> {
            assertThat((Integer) invocation.getArgument(3)).isEqualTo(4);
            TranscriptBatchVerificationResult result = invocation.getArgument(9);
            assertThat(result.successes()).hasSize(1);
            assertThat(result.successes().getFirst().application().rowNumber()).isEqualTo(4);
            assertThat(result.failures()).hasSize(3);
            assertThat(result.failures()).extracting(TranscriptBatchVerificationResult.Failure::code)
                .containsExactly("ESSAY_SCORE_REQUIRED", "ESSAY_SCORE_REQUIRED", "ALTERNATIVE_ACADEMIC_INPUT_REQUIRED");
            return null;
        }).when(validationExcelWriter).write(anyString(), anyString(), anyString(), anyInt(), anyInt(),
            anyList(), anyList(), anyList(), anyList(), any(), any());
        service.export(80L);
        verify(batchVerificationService).buildIntermediateCalculations(rule, verification);
        org.mockito.Mockito.verifyNoMoreInteractions(batchVerificationService);
    }

}
