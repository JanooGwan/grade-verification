package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TransactionObservationTest {
    @Test
    void recordsCommitAndRollbackWithoutInventingCommitDuration() {
        var logger = (Logger) LoggerFactory.getLogger(TransactionObservation.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            TransactionObservation.register("import", "45");
            var sync = TransactionSynchronizationManager.getSynchronizations().getFirst();
            sync.beforeCommit(false);
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            TransactionObservation.register("export", "46");
            var rollback = TransactionSynchronizationManager.getSynchronizations().getLast();
            rollback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            var messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).anySatisfy(m -> assertThat(m).contains("outcome=COMMITTED", "beforeCommitToCompletionMs="));
            assertThat(messages).anySatisfy(m -> assertThat(m).contains("outcome=ROLLED_BACK", "beforeCommitToCompletionMs=null"));
        } finally {
            TransactionSynchronizationManager.clear();
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
