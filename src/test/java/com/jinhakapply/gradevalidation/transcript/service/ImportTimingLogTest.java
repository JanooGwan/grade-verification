package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ImportTimingLogTest {
    @Test
    void logsStartAndCountAndDoesNotLeakFailureDetails() {
        Logger logger = (Logger) LoggerFactory.getLogger(ImportTimingLog.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(ImportTimingLog.measure(45L, "delete_courses", () -> 7, Integer::intValue)).isEqualTo(7);
            assertThatThrownBy(() -> ImportTimingLog.measure(45L, "delete_courses", () -> {
                throw new IllegalStateException("PRIVATE_VALUE");
            }, (Integer n) -> n)).isInstanceOf(IllegalStateException.class);
            var messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).hasSize(4);
            assertThat(messages.get(0)).contains("mjc_import_detail_start", "importId=45", "delete_courses");
            assertThat(messages.get(1)).contains("rows=7", "status=OK");
            assertThat(messages.get(3)).contains("status=FAILED").doesNotContain("PRIVATE_VALUE");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
