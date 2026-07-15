package com.jinhakapply.gradevalidation.operation.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.stereotype.Component;

@Component
public class OperationalMetrics {
    private final LocalDateTime startedAt = LocalDateTime.now();
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder errorRequests = new LongAdder();
    private final LongAdder totalDurationMillis = new LongAdder();
    private final AtomicLong maxDurationMillis = new AtomicLong();
    private final ConcurrentHashMap<String, EndpointCounter> endpoints = new ConcurrentHashMap<>();

    public void record(String method, String path, int status, long durationMillis) {
        totalRequests.increment();
        totalDurationMillis.add(durationMillis);
        maxDurationMillis.accumulateAndGet(durationMillis, Math::max);
        if (status >= 400) errorRequests.increment();
        endpoints.computeIfAbsent(method + " " + normalize(path), ignored -> new EndpointCounter())
            .record(status, durationMillis);
    }

    public Snapshot snapshot() {
        long total = totalRequests.sum();
        return new Snapshot(
            startedAt, total, errorRequests.sum(), total == 0 ? 0 : totalDurationMillis.sum() / total,
            maxDurationMillis.get(), endpoints.entrySet().stream()
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .sorted(Comparator.comparingLong(EndpointSnapshot::requests).reversed())
                .limit(20).toList()
        );
    }

    private String normalize(String path) {
        return path.replaceAll("/\\d+(?=/|$)", "/{id}");
    }

    private static final class EndpointCounter {
        private final LongAdder requests = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder duration = new LongAdder();

        void record(int status, long millis) {
            requests.increment();
            duration.add(millis);
            if (status >= 400) errors.increment();
        }

        EndpointSnapshot snapshot(String endpoint) {
            long count = requests.sum();
            return new EndpointSnapshot(endpoint, count, errors.sum(), count == 0 ? 0 : duration.sum() / count);
        }
    }

    public record Snapshot(
        LocalDateTime startedAt,
        long totalRequests,
        long errorRequests,
        long averageDurationMillis,
        long maxDurationMillis,
        List<EndpointSnapshot> endpoints
    ) {}

    public record EndpointSnapshot(String endpoint, long requests, long errors, long averageDurationMillis) {}
}
