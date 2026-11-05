package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class WorkflowTraceTest {
    @Test
    void logsSuccessAndFailureWithoutExceptionDetails() {
        Logger logger = (Logger) LoggerFactory.getLogger(WorkflowTrace.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(WorkflowTrace.measure("verification", 45L, "load_courses", () -> 42)).isEqualTo(42);
            assertThatThrownBy(() -> WorkflowTrace.measure("export", 45L, "result_sheet", () -> {
                throw new IllegalStateException("PRIVATE_VALUE");
            })).isInstanceOf(IllegalStateException.class);
            var messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).hasSize(4);
            assertThat(messages.get(0)).contains("workflow_detail_start", "sourceImportId=45", "load_courses");
            assertThat(messages.get(1)).contains("status=OK", "elapsedMs=");
            assertThat(messages.get(3)).contains("status=FAILED").doesNotContain("PRIVATE_VALUE");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
