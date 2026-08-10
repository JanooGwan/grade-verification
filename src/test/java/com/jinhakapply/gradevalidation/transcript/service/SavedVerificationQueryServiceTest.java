package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
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

    private SavedVerificationQueryService service;

    @BeforeEach
    void setUp() {
        service = new SavedVerificationQueryService(repository, objectMapper);
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
}
