package com.jinhakapply.gradevalidation.transcript.service;

import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/** Fixed labels and aggregate counts only. Close without success records failure. */
@Slf4j
final class WorkflowTrace {
    private WorkflowTrace() { }

    static Scope start(String workflow, Long sourceImportId, String operation) {
        return new Scope(workflow, sourceImportId, operation);
    }

    static <T> T measure(String workflow, Long sourceImportId, String operation, Supplier<T> action) {
        try (var scope = start(workflow, sourceImportId, operation)) {
            T result = action.get();
            scope.success();
            return result;
        }
    }

    static final class Scope implements AutoCloseable {
        private final String workflow;
        private final Long sourceImportId;
        private final String operation;
        private final long started = System.nanoTime();
        private boolean success;
        private boolean closed;

        private Scope(String workflow, Long sourceImportId, String operation) {
            this.workflow = workflow;
            this.sourceImportId = sourceImportId;
            this.operation = operation;
            log.info("workflow_detail_start workflow={} sourceImportId={} operation={}", workflow, sourceImportId, operation);
        }
        void success() { success = true; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            log.info("workflow_detail_end workflow={} sourceImportId={} operation={} elapsedMs={} status={}",
                workflow, sourceImportId, operation, (System.nanoTime() - started) / 1_000_000, success ? "OK" : "FAILED");
        }
    }
}
