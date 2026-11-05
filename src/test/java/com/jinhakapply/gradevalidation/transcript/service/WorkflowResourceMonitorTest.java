package com.jinhakapply.gradevalidation.transcript.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class WorkflowResourceMonitorTest {
    @Test
    void disabledMonitorDoesNotQueryDatabase() {
        DataSource dataSource = mock(DataSource.class);
        var monitor = new WorkflowResourceMonitor(dataSource, false, "D:");
        try (var ignored = monitor.start("import", "45")) { }
        verifyNoInteractions(dataSource);
    }

    @Test
    void enabledMonitorRecordsJvmStartAndEndWithoutDatabaseData() {
        var logger = (Logger) LoggerFactory.getLogger(WorkflowResourceMonitor.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var monitor = new WorkflowResourceMonitor(mock(DataSource.class), true, "D:");
            try (var ignored = monitor.start("import", "45")) { }
            var messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).anySatisfy(m -> assertThat(m).contains("workflow_jvm", "phase=start", "heapUsedBytes="));
            assertThat(messages).anySatisfy(m -> assertThat(m).contains("workflow_jvm", "phase=end", "gcMillisDelta="));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void mysqlSampleQueriesOnlyAggregateAndChecksPerformanceSchema() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet enabled = mock(ResultSet.class);
        ResultSet waits = mock(ResultSet.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT @@performance_schema")).thenReturn(enabled);
        when(enabled.next()).thenReturn(true);
        when(enabled.getInt(1)).thenReturn(1);
        when(statement.executeQuery("SELECT COUNT(*) FROM performance_schema.data_lock_waits")).thenReturn(waits);
        when(waits.next()).thenReturn(true);
        when(waits.getLong(1)).thenReturn(2L);
        var monitor = new WorkflowResourceMonitor(ds, true, "D:");
        monitor.sampleMysql("import", "45");
        verify(statement).setQueryTimeout(2);
        verify(conn).close();
    }

    @Test
    void disabledPerformanceSchemaIsNotReportedAsZeroWaits() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet enabled = mock(ResultSet.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT @@performance_schema")).thenReturn(enabled);
        when(enabled.next()).thenReturn(true);
        when(enabled.getInt(1)).thenReturn(0);
        var monitor = new WorkflowResourceMonitor(ds, true, "D:");
        assertThatThrownBy(() -> monitor.sampleMysql("import", "45")).isInstanceOf(IllegalStateException.class);
        verify(statement, org.mockito.Mockito.never()).executeQuery("SELECT COUNT(*) FROM performance_schema.data_lock_waits");
    }
}
