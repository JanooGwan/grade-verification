package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.admission.service.EvaluationRuleMatcher;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

class VerificationComputeOptimizationTest {
    @ParameterizedTest
    @ValueSource(ints = {2026, 2027})
    void persistenceAndExportPathsKeepIdenticalFullResultsWarningsAndFailures(int year) {
        var input = VerificationComputeFixtures.create(year, 100, true);
        var service = service(input, new EvaluationRuleMatcher());
        Map<Long, GradeVerificationResponse> fullPayload = new LinkedHashMap<>();
        Map<Long, GradeVerificationResponse> storedPayload = new LinkedHashMap<>();
        var export = service.verify(1L, year, input.applications(), input.courses(), input.schoolInfo(), input.metadata(),
            (application, result) -> fullPayload.put(application.applicationId(), result));
        var stored = service.verifyForPersistence(1L, year, input.applications(), input.courses(), input.schoolInfo(), input.metadata(),
            (application, result) -> storedPayload.put(application.applicationId(), result));
        assertThat(storedPayload).isEqualTo(fullPayload);
        var mapper = new ObjectMapper();
        assertThat(mapper.writeValueAsString(storedPayload)).isEqualTo(mapper.writeValueAsString(fullPayload));
        assertThat(stored.failures()).isEqualTo(export.failures());
        assertThat(stored.successes()).extracting(TranscriptBatchVerificationResult.Success::verification)
            .containsExactlyElementsOf(export.successes().stream().map(TranscriptBatchVerificationResult.Success::verification).toList());
        assertThat(export.successes().stream().mapToInt(success -> success.selectedCourses().size()).sum()).isPositive();
        assertThat(stored.successes()).allSatisfy(success -> {
            assertThat(success.selectedCourses()).isEmpty();
            assertThat(success.intermediateCalculations()).isEmpty();
        });
        assertThat(fullPayload.values()).anySatisfy(result -> assertThat(result.calculations()).isNotEmpty());
        assertThat(stored.failures()).extracting(TranscriptBatchVerificationResult.Failure::code)
            .contains("MJC_SOURCE_GRADE_REVIEW_REQUIRED", "STUDENT_RECORD_MISSING", "PREVIOUS_COLLEGE_RECORD_REQUIRED");
    }

    @Test
    void matchesRulesOncePerExactTrackAndUnitAndDropsTheCacheBetweenRuns() {
        var input = VerificationComputeFixtures.create(2027, 1, false);
        var repository = mock(EvaluationRuleRepository.class);
        var matcher = spy(new EvaluationRuleMatcher());
        var calculator = mock(EvaluationService.class);
        var rule = input.rules().getFirst();
        when(repository.findAllByUniversityIdAndAdmissionYearAndStatus(1L, 2027, EvaluationRuleStatus.PUBLISHED))
            .thenReturn(List.of(rule), List.of());
        var service = new TranscriptBatchVerificationService(repository, calculator, matcher);
        var applicants = new ArrayList<TransferApplicationRow>();
        for (int i = 1; i <= 20_000; i++) applicants.add(new TransferApplicationRow(i, 2027, "SYNTHETIC-" + i,
            "0105", "특별[일반고]", "UNIT", i % 2 == 0 ? "컴퓨터공학과" : "컴퓨터공학과 ", 2027));
        var first = service.verify(1L, 2027, applicants, List.of());
        assertThat(first.failures()).hasSize(20_000).allSatisfy(failure -> assertThat(failure.code()).isEqualTo("COURSE_NOT_FOUND"));
        verify(matcher, times(2)).matchesAdmissionType(eq(rule), anyString(), anyString());
        verify(matcher, times(2)).matchesRecruitmentUnit(eq(rule), anyString());
        var second = service.verify(1L, 2027, applicants, List.of());
        assertThat(second.failures()).hasSize(20_000).allSatisfy(failure -> assertThat(failure.code()).isEqualTo("RULE_NOT_FOUND"));
        verifyNoInteractions(calculator);
    }

    @Test
    void buildsArchiveIntermediateValuesOnceAndKeepsExportDetailsWhenRequested() {
        var input = VerificationComputeFixtures.create(2027, 1, true);
        var service = spy(service(input, new EvaluationRuleMatcher()));
        var result = service.verify(1L, 2027, input.applications(), input.courses(), input.schoolInfo(), input.metadata(),
            (application, score) -> { });
        assertThat(result.successes()).hasSize(1);
        verify(service, times(1)).buildIntermediateCalculations(any(), any());
        assertThat(result.successes().getFirst().intermediateCalculations()).isNotEmpty();
        clearInvocations(service);
        result = service.verifyForPersistence(1L, 2027, input.applications(), input.courses(), input.schoolInfo(), Map.of(),
            (application, score) -> { });
        assertThat(result.successes()).hasSize(1);
        verify(service, never()).buildIntermediateCalculations(any(), any());
    }

    private TranscriptBatchVerificationService service(VerificationComputeFixtures.Fixture input, EvaluationRuleMatcher matcher) {
        var repository = mock(EvaluationRuleRepository.class);
        when(repository.findAllByUniversityIdAndAdmissionYearAndStatus(1L, input.year(), EvaluationRuleStatus.PUBLISHED))
            .thenReturn(input.rules());
        return new TranscriptBatchVerificationService(repository, new EvaluationService(null, null, null), matcher);
    }
}
