package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.transcript.repository.SavedVerificationQueryRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

class WorkflowTimingLogTest {
    @Test
    void failedExportRecordsReachedDatabasePhaseWithoutLoggingExceptionContents() {
        var repository = mock(SavedVerificationQueryRepository.class);
        when(repository.findBatch(80L)).thenThrow(new IllegalStateException("SYNTHETIC-PRIVATE-VALUE"));
        var service = new SavedVerificationQueryService(repository, mock(ObjectMapper.class),
            mock(EvaluationRuleRepository.class), mock(TranscriptBatchVerificationService.class),
            mock(TranscriptValidationExcelWriter.class), mock(SyuSavedVerificationExcelWriter.class));
        Logger logger = (Logger) LoggerFactory.getLogger(SavedVerificationQueryService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> service.writeExport(80L, new ByteArrayOutputStream()))
                .isInstanceOf(IllegalStateException.class);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                .contains("verification_export_timings", "status=FAILED", "batch_db", "export_total")
                .doesNotContain("SYNTHETIC-PRIVATE-VALUE");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
