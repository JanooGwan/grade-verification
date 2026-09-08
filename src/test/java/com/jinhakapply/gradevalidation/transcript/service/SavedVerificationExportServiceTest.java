package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executor;

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
