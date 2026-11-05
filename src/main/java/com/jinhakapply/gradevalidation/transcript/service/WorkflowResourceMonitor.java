package com.jinhakapply.gradevalidation.transcript.service;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Best-effort sampling on daemon threads; no applicant data, SQL text, or exception messages. */
@Component
@Slf4j
class WorkflowResourceMonitor {
    private static final ScheduledThreadPoolExecutor EXECUTOR = new ScheduledThreadPoolExecutor(2, task -> {
        Thread thread = new Thread(task, "workflow-resource-monitor");
        thread.setDaemon(true);
        return thread;
    });
    static { EXECUTOR.setRemoveOnCancelPolicy(true); }
    private final DataSource dataSource;
    private final boolean enabled;
    private final String drive;

    WorkflowResourceMonitor(DataSource dataSource,
        @Value("${app.monitoring.workflow-resources.enabled:true}") boolean enabled,
        @Value("${app.monitoring.workflow-resources.drive:D:}") String drive) {
        this.dataSource = dataSource;
        this.enabled = enabled;
        this.drive = drive.matches("[A-Za-z]:") ? drive.toUpperCase(Locale.ROOT) : "";
    }

    Session start(String workflow, String id) {
        if (!enabled) return new Session(null, workflow, id, null);
        try {
            JvmSnapshot start = snapshot();
            logJvm(workflow, id, "start", start, null);
            ScheduledFuture<?> future = EXECUTOR.scheduleWithFixedDelay(() -> sample(workflow, id), 0, 30, TimeUnit.SECONDS);
            return new Session(future, workflow, id, start);
        } catch (RuntimeException ignored) {
            logUnavailable(workflow, id, "monitor_start");
            return new Session(null, workflow, id, null);
        }
    }

    private void sample(String workflow, String id) {
        try { logJvm(workflow, id, "sample", snapshot(), null); } catch (RuntimeException ignored) { logUnavailable(workflow, id, "jvm"); }
        try { sampleWindows(workflow, id); }
        catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
        catch (Exception ignored) { logUnavailable(workflow, id, "windows_disk_memory"); }
        try { sampleMysql(workflow, id); } catch (Exception ignored) { logUnavailable(workflow, id, "mysql_row_lock_waits"); }
    }

    private void sampleWindows(String workflow, String id) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows") || drive.isEmpty()) {
            logUnavailable(workflow, id, "windows_disk_memory");
            return;
        }
        String script = "$ErrorActionPreference='Stop'; $c=Get-Counter '\\LogicalDisk(" + drive + ")\\Avg. Disk sec/Transfer',"
            + "'\\LogicalDisk(" + drive + ")\\Avg. Disk Queue Length','\\LogicalDisk(" + drive + ")\\% Idle Time',"
            + "'\\LogicalDisk(" + drive + ")\\Disk Read Bytes/sec','\\LogicalDisk(" + drive + ")\\Disk Write Bytes/sec',"
            + "'\\Memory\\Available MBytes' -SampleInterval 1 -MaxSamples 1; "
            + "$c.CounterSamples | ForEach-Object { $_.CookedValue.ToString('R',[Globalization.CultureInfo]::InvariantCulture) }";
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!process.waitFor(8, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException(); }
            if (process.exitValue() != 0) throw new IllegalStateException();
            String[] lines = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim().split("\\R");
            if (lines.length != 6) throw new IllegalStateException();
            double[] values = new double[6];
            for (int i = 0; i < values.length; i++) {
                values[i] = Double.parseDouble(lines[i]);
                if (!Double.isFinite(values[i])) throw new IllegalStateException();
            }
            log.info("workflow_resource_sample workflow={} id={} drive={} diskTransferMs={} diskQueue={} diskIdlePercent={} readBytesPerSec={} writeBytesPerSec={} availableMemoryMB={}",
                workflow, id, drive, values[0] * 1000, values[1], values[2], values[3], values[4], values[5]);
        } finally { process.destroyForcibly(); }
    }

    void sampleMysql(String workflow, String id) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(2);
            try (ResultSet enabled = statement.executeQuery("SELECT @@performance_schema")) {
                if (!enabled.next() || enabled.getInt(1) != 1) throw new IllegalStateException();
            }
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM performance_schema.data_lock_waits")) {
                if (!result.next()) throw new IllegalStateException();
                log.info("workflow_mysql_sample workflow={} id={} globalInnoDbRowLockWaits={}", workflow, id, result.getLong(1));
            }
        }
    }

    private static void logUnavailable(String workflow, String id, String resource) {
        log.info("workflow_resource_unavailable workflow={} id={} resource={}", workflow, id, resource);
    }

    private static JvmSnapshot snapshot() {
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long count = 0, millis = 0;
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc.getCollectionCount() >= 0) count += gc.getCollectionCount();
            if (gc.getCollectionTime() >= 0) millis += gc.getCollectionTime();
        }
        return new JvmSnapshot(heap.getUsed(), heap.getCommitted(), heap.getMax(), count, millis);
    }

    private static void logJvm(String workflow, String id, String phase, JvmSnapshot value, JvmSnapshot start) {
        log.info("workflow_jvm workflow={} id={} phase={} heapUsedBytes={} heapCommittedBytes={} heapMaxBytes={} gcCount={} gcMillis={} gcCountDelta={} gcMillisDelta={}",
            workflow, id, phase, value.used, value.committed, value.max, value.count, value.millis,
            start == null ? null : Math.max(0, value.count - start.count), start == null ? null : Math.max(0, value.millis - start.millis));
    }

    private record JvmSnapshot(long used, long committed, long max, long count, long millis) { }

    static final class Session implements AutoCloseable {
        private final ScheduledFuture<?> future;
        private final String workflow;
        private final String id;
        private final JvmSnapshot start;
        private Session(ScheduledFuture<?> future, String workflow, String id, JvmSnapshot start) {
            this.future = future; this.workflow = workflow; this.id = id; this.start = start;
        }
        @Override public void close() {
            if (future != null) future.cancel(true);
            if (start != null) try { logJvm(workflow, id, "end", snapshot(), start); }
            catch (RuntimeException ignored) { logUnavailable(workflow, id, "jvm"); }
        }
    }
}
