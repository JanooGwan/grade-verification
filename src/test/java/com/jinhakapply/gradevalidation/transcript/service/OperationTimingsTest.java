package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class OperationTimingsTest {
    @Test
    void aggregatesRepeatedAndNestedScopesWithoutDoubleCountingTheIndividualScopes() {
        AtomicLong clock = new AtomicLong();
        OperationTimings timing = new OperationTimings(clock::get);
        try (var outer = timing.start("outer")) {
            clock.addAndGet(2_000_000);
            try (var inner = timing.start("inner")) { clock.addAndGet(3_000_000); }
            try (var inner = timing.start("inner")) { clock.addAndGet(5_000_000); }
        }
        assertThat(timing.snapshotMillis()).containsEntry("outer", 10L).containsEntry("inner", 8L);
        assertThat(timing.snapshotMillis()).doesNotContainKey("not_reached");
    }

    @Test
    void recordsElapsedTimeWhenTheOperationThrowsAndDoesNotChangeTheException() {
        AtomicLong clock = new AtomicLong();
        OperationTimings timing = new OperationTimings(clock::get);
        assertThatThrownBy(() -> timing.measure("db", () -> {
            clock.addAndGet(7_000_000);
            throw new IllegalStateException("synthetic failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("synthetic failure");
        assertThat(timing.snapshotMillis()).containsEntry("db", 7L);
    }
}
