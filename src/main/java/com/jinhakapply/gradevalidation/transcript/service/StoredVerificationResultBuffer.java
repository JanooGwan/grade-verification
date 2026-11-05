package com.jinhakapply.gradevalidation.transcript.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import lombok.extern.slf4j.Slf4j;

import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository;
import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository.ApplicationVerificationRow;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;

/** Bounded write buffer only. The calculator and its serialized result are unchanged. */
@Slf4j
final class StoredVerificationResultBuffer {
    private static final long FALLBACK_BYTES = 512L * 1024;
    private static final long MAXIMUM_BATCH_BYTES = 4L * 1024 * 1024;
    private final BatchVerificationRunRepository repository;
    private final Long sourceImportId;
    private final IntConsumer savedListener;
    private final int maximumRows;
    private final long maximumBytes;
    private final List<ApplicationVerificationRow> rows = new ArrayList<>();
    private long estimatedBytes;
    private int savedResults;
    private int batchNumber;

    StoredVerificationResultBuffer(BatchVerificationRunRepository repository, Long sourceImportId, IntConsumer savedListener) {
        this(repository, sourceImportId, savedListener, 200, FALLBACK_BYTES);
    }

    static long budgetForPacketLimit(Long packetLimit) {
        if (packetLimit == null || packetLimit <= 0) return FALLBACK_BYTES;
        // Leave 75% for protocol, driver escaping and other statement overhead.
        return Math.max(1, Math.min(MAXIMUM_BATCH_BYTES, packetLimit / 4));
    }

    StoredVerificationResultBuffer(BatchVerificationRunRepository repository, Long sourceImportId, IntConsumer savedListener,
        int maximumRows, long maximumBytes) {
        if (maximumRows < 1 || maximumRows > 200 || maximumBytes < 1) throw new IllegalArgumentException();
        this.repository = repository;
        this.sourceImportId = sourceImportId;
        this.savedListener = savedListener;
        this.maximumRows = maximumRows;
        this.maximumBytes = maximumBytes;
    }

    void add(Long applicationId, GradeVerificationResponse result, String json) {
        if (applicationId == null) throw new IllegalStateException("DB 지원정보 식별자가 없어 검증 결과를 저장할 수 없습니다.");
        // Budget for UTF-8 and worst-case SQL string escaping, plus the other row parameters.
        long bytes = (long) json.length() * 6 + 128;
        if (!rows.isEmpty() && estimatedBytes + bytes > maximumBytes) flush();
        rows.add(new ApplicationVerificationRow(applicationId, result, json));
        estimatedBytes += bytes;
        if (rows.size() >= maximumRows || estimatedBytes >= maximumBytes) flush();
    }

    void flush() {
        if (rows.isEmpty()) return;
        int batch = ++batchNumber;
        int size = rows.size();
        long started = System.nanoTime();
        boolean success = false;
        int inserted;
        log.info("verification_batch_start sourceImportId={} batch={} rows={} estimatedBytes={}", sourceImportId, batch, size, estimatedBytes);
        try {
            inserted = repository.insertBatch(sourceImportId, List.copyOf(rows));
            if (inserted != rows.size()) throw new IllegalStateException("검증 결과 저장 건수가 일치하지 않습니다.");
            success = true;
        } finally {
            log.info("verification_batch_end sourceImportId={} batch={} rows={} elapsedMs={} status={}",
                sourceImportId, batch, size, (System.nanoTime() - started) / 1_000_000, success ? "OK" : "FAILED");
        }
        savedResults += inserted;
        rows.clear();
        estimatedBytes = 0;
        savedListener.accept(savedResults);
    }

    int savedResults() { return savedResults; }
}
