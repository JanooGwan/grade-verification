package com.jinhakapply.gradevalidation.transcript.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository;
import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository.ApplicationVerificationRow;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;

/** Bounded write buffer only. The calculator and its serialized result are unchanged. */
final class StoredVerificationResultBuffer {
    private final BatchVerificationRunRepository repository;
    private final Long sourceImportId;
    private final IntConsumer savedListener;
    private final int maximumRows;
    private final long maximumBytes;
    private final List<ApplicationVerificationRow> rows = new ArrayList<>();
    private long estimatedBytes;
    private int savedResults;

    StoredVerificationResultBuffer(BatchVerificationRunRepository repository, Long sourceImportId, IntConsumer savedListener) {
        this(repository, sourceImportId, savedListener, 200, 512 * 1024);
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
        // Three bytes per UTF-16 character upper-bounds UTF-8 size, including surrogate pairs.
        long bytes = (long) json.length() * 3 + 128;
        if (!rows.isEmpty() && estimatedBytes + bytes > maximumBytes) flush();
        rows.add(new ApplicationVerificationRow(applicationId, result, json));
        estimatedBytes += bytes;
        if (rows.size() >= maximumRows || estimatedBytes >= maximumBytes) flush();
    }

    void flush() {
        if (rows.isEmpty()) return;
        int inserted = repository.insertBatch(sourceImportId, List.copyOf(rows));
        if (inserted != rows.size()) throw new IllegalStateException("검증 결과 저장 건수가 일치하지 않습니다.");
        savedResults += inserted;
        rows.clear();
        estimatedBytes = 0;
        savedListener.accept(savedResults);
    }

    int savedResults() { return savedResults; }
}
