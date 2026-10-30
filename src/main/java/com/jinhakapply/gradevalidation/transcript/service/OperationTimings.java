package com.jinhakapply.gradevalidation.transcript.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Per-job monotonic, aggregate timings. Labels are fixed in code and never contain applicant data. */
final class OperationTimings {
    private final LongSupplier clock;
    private final Map<String, Long> elapsedNanos = new LinkedHashMap<>();

    OperationTimings() { this(System::nanoTime); }
    OperationTimings(LongSupplier clock) { this.clock = clock; }

    Scope start(String name) { return new Scope(name, clock.getAsLong()); }

    <T> T measure(String name, Supplier<T> task) {
        try (Scope ignored = start(name)) { return task.get(); }
    }

    long millis(String name) { return elapsedNanos.getOrDefault(name, 0L) / 1_000_000L; }

    /** Snapshots include only completed scopes; an absent key means the stage was not reached. */
    Map<String, Long> snapshotMillis() {
        Map<String, Long> result = new LinkedHashMap<>();
        elapsedNanos.forEach((key, value) -> result.put(key, value / 1_000_000L));
        return Map.copyOf(result);
    }

    final class Scope implements AutoCloseable {
        private final String name;
        private final long started;
        private boolean closed;
        private Scope(String name, long started) { this.name = name; this.started = started; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            elapsedNanos.merge(name, Math.max(0, clock.getAsLong() - started), Long::sum);
        }
    }
}
