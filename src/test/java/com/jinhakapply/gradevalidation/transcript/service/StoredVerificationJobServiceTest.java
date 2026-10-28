package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import com.jinhakapply.gradevalidation.transcript.dto.StoredVerificationProgress;

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
        when(verificationService.persist(eq(4L), eq(2026), any())).thenReturn(result);
        Executor sameThreadExecutor = Runnable::run;
        var service = new StoredVerificationJobService(verificationService, sameThreadExecutor);

        var started = service.start(4L, 2026);
        var status = service.status(started.jobId());

        assertThat(status.status()).isEqualTo("COMPLETED");
        assertThat(status.result()).isEqualTo(result);
        assertThat(status.completedAt()).isNotNull();
        assertThat(status.progress().stage()).isEqualTo("COMPLETED");
        assertThat(status.progress().savedResults()).isEqualTo(15_500);
    }

    @Test
    void exposesFailureWithoutBreakingThePollingRequest() {
        when(verificationService.persist(eq(4L), eq(2026), any())).thenThrow(new IllegalStateException("database failed"));
        Executor sameThreadExecutor = Runnable::run;
        var service = new StoredVerificationJobService(verificationService, sameThreadExecutor);

        var started = service.start(4L, 2026);
        var status = service.status(started.jobId());

        assertThat(status.status()).isEqualTo("FAILED");
        assertThat(status.message()).contains("예상하지 못한 오류");
    }

    @Test
    void exposesProvisionalCountsWithoutDeclaringCompletionBeforePersistReturns() {
        var pending = new AtomicReference<Runnable>();
        var service = new StoredVerificationJobService(verificationService, pending::set);
        var started = service.start(4L, 2027);
        assertThat(started.progress().stage()).isEqualTo("QUEUED");
        assertThat(service.start(4L, 2027).jobId()).isEqualTo(started.jobId());
        doAnswer(call -> {
            Consumer<StoredVerificationProgress> progress = call.getArgument(2);
            progress.accept(new StoredVerificationProgress("COMMITTING", 10, 9, 1));
            var beforeCommitReturns = service.status(started.jobId());
            assertThat(beforeCommitReturns.status()).isEqualTo("PROCESSING");
            assertThat(beforeCommitReturns.result()).isNull();
            assertThat(beforeCommitReturns.progress().savedResults()).isEqualTo(9);
            return new StoredVerificationPersistenceResponse(27L, 10, 9, 1, 0, LocalDateTime.now());
        }).when(verificationService).persist(eq(4L), eq(2027), any());
        pending.get().run();
        assertThat(service.status(started.jobId()).status()).isEqualTo("COMPLETED");
    }
}
