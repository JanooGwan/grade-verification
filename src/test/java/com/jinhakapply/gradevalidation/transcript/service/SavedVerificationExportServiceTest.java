package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavedVerificationExportServiceTest {
    @Mock
    private SavedVerificationQueryService queryService;

    private SavedVerificationExportService service;

    @Test
    void longRunningJobsStayVisibleAndExpireOnlyAfterCompletion() {
        var now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        var pending = new AtomicReference<Runnable>();
        service = new SavedVerificationExportService(queryService, pending::set, clock);
        var started = service.start(24L);
        now.set(now.get().plus(Duration.ofHours(2)));
        assertThat(service.status(started.exportId()).status()).isEqualTo("PROCESSING");
        assertThat(service.activeJobs()).extracting(job -> job.exportId()).containsExactly(started.exportId());
        assertThat(service.start(24L).exportId()).isEqualTo(started.exportId());
        pending.get().run();
        assertThat(service.status(started.exportId()).status()).isEqualTo("READY");
        assertThat(service.activeJobs()).isEmpty();
        now.set(now.get().plus(Duration.ofMinutes(61)));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.status(started.exportId()))
            .isInstanceOf(com.jinhakapply.gradevalidation.global.exception.CustomException.class);
    }

    @AfterEach
    void tearDown() {
        if (service != null) service.cleanup();
    }

    @Test
    void generatesTheFileOutsideTheRequestAndReusesTheReadyJob() throws Exception {
        byte[] excel = new byte[] {1, 2, 3, 4};
        doAnswer(invocation -> {
            java.io.OutputStream output = invocation.getArgument(1);
            output.write(excel);
            return null;
        }).when(queryService).writeExport(eq(24L), org.mockito.ArgumentMatchers.any());
        Executor sameThreadExecutor = Runnable::run;
        service = new SavedVerificationExportService(queryService, sameThreadExecutor);

        var started = service.start(24L);
        var status = service.status(started.exportId());
        var reused = service.start(24L);
        var output = new ByteArrayOutputStream();
        service.writeFile(started.exportId(), output);

        assertThat(started.status()).isEqualTo("PROCESSING");
        assertThat(status.status()).isEqualTo("READY");
        assertThat(reused.exportId()).isEqualTo(started.exportId());
        assertThat(reused.status()).isEqualTo("READY");
        assertThat(output.toByteArray()).isEqualTo(excel);
    }
}
