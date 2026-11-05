package com.jinhakapply.gradevalidation.transcript.service;

import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/** Logs fixed operation names and counts only; never input values or exception messages. */
@Slf4j
final class ImportTimingLog {
    private ImportTimingLog() { }

    static <T> T measure(Long importId, String operation, Supplier<T> action, java.util.function.ToIntFunction<T> count) {
        long start = System.nanoTime();
        log.info("mjc_import_detail_start importId={} operation={}", importId, operation);
        boolean success = false;
        try {
            T result = action.get();
            success = true;
            log.info("mjc_import_detail_end importId={} operation={} elapsedMs={} rows={} status=OK",
                importId, operation, (System.nanoTime() - start) / 1_000_000, count.applyAsInt(result));
            return result;
        } finally {
            if (!success) log.warn("mjc_import_detail_end importId={} operation={} elapsedMs={} status=FAILED",
                importId, operation, (System.nanoTime() - start) / 1_000_000);
        }
    }
}
