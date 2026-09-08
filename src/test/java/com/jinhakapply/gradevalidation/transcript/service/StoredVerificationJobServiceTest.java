package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;

import com.jinhakapply.gradevalidation.transcript.dto.StoredVerificationPersistenceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoredVerificationJobServiceTest {
    @Mock
    private StoredTranscriptVerificationService verificationService;

    @Test
    void completesVerificationOutsideTheRequestAndReturnsSavedResult() {
        var result = new StoredVerificationPersistenceResponse(
            27L, 15_503, 15_500, 3, 0, LocalDateTime.of(2026, 9, 8, 12, 0)
        );
        when(verificationService.persist(4L, 2026)).thenReturn(result);
        Executor sameThreadExecutor = Runnable::run;
        var service = new StoredVerificationJobService(verificationService, sameThreadExecutor);

        var started = service.start(4L, 2026);
        var status = service.status(started.jobId());

        assertThat(status.status()).isEqualTo("COMPLETED");
        assertThat(status.result()).isEqualTo(result);
        assertThat(status.completedAt()).isNotNull();
    }

    @Test
    void exposesFailureWithoutBreakingThePollingRequest() {
        when(verificationService.persist(4L, 2026)).thenThrow(new IllegalStateException("database failed"));
        Executor sameThreadExecutor = Runnable::run;
        var service = new StoredVerificationJobService(verificationService, sameThreadExecutor);

        var started = service.start(4L, 2026);
        var status = service.status(started.jobId());

        assertThat(status.status()).isEqualTo("FAILED");
        assertThat(status.message()).contains("예상하지 못한 오류");
    }
}
