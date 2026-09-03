package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.VERIFICATION_EXPORT_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.VERIFICATION_EXPORT_NOT_READY;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.VERIFICATION_EXPORT_QUEUE_FULL;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationExportStartResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationExportStatusResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

@Service
public class SavedVerificationExportService {
    private static final Logger log = LoggerFactory.getLogger(SavedVerificationExportService.class);
    private static final Duration JOB_TTL = Duration.ofHours(1);
    private static final String PROCESSING = "PROCESSING";
    private static final String READY = "READY";
    private static final String FAILED = "FAILED";

    private final SavedVerificationQueryService queryService;
    private final Executor executor;
    private final Map<UUID, ExportJob> jobs = new ConcurrentHashMap<>();
    private final Map<Long, UUID> activeJobsBySourceImport = new ConcurrentHashMap<>();

    public SavedVerificationExportService(
        SavedVerificationQueryService queryService,
        @Qualifier("savedVerificationExportExecutor") Executor executor
    ) {
        this.queryService = queryService;
        this.executor = executor;
    }

    public synchronized SavedVerificationExportStartResponse start(Long sourceImportId) {
        cleanupExpired();
        UUID activeId = activeJobsBySourceImport.get(sourceImportId);
        ExportJob active = activeId == null ? null : jobs.get(activeId);
        if (active != null && !FAILED.equals(active.status)) {
            return new SavedVerificationExportStartResponse(active.id, active.sourceImportId, active.status);
        }

        ExportJob job = new ExportJob(UUID.randomUUID(), sourceImportId, Instant.now());
        jobs.put(job.id, job);
        activeJobsBySourceImport.put(sourceImportId, job.id);
        try {
            executor.execute(() -> generate(job));
        } catch (TaskRejectedException exception) {
            jobs.remove(job.id);
            activeJobsBySourceImport.remove(sourceImportId, job.id);
            throw CustomException.of(VERIFICATION_EXPORT_QUEUE_FULL);
        }
        return new SavedVerificationExportStartResponse(job.id, sourceImportId, PROCESSING);
    }

    public SavedVerificationExportStatusResponse status(UUID exportId) {
        cleanupExpired();
        ExportJob job = requireJob(exportId);
        return new SavedVerificationExportStatusResponse(
            job.id, job.sourceImportId, job.status, job.message
        );
    }

    public void writeFile(UUID exportId, OutputStream output) throws IOException {
        ExportJob job = requireJob(exportId);
        if (!READY.equals(job.status) || job.file == null) {
            throw CustomException.of(VERIFICATION_EXPORT_NOT_READY);
        }
        try (var input = Files.newInputStream(job.file)) {
            input.transferTo(output);
        }
    }

    public Long sourceImportId(UUID exportId) {
        return requireJob(exportId).sourceImportId;
    }

    private void generate(ExportJob job) {
        Path file = null;
        try {
            file = Files.createTempFile("saved-verification-export-", ".xlsx");
            Files.write(file, queryService.export(job.sourceImportId));
            job.file = file;
            job.status = READY;
            job.message = null;
        } catch (Exception exception) {
            deleteQuietly(file);
            job.status = FAILED;
            job.message = "Excel 파일을 생성하지 못했습니다. 다시 시도해 주세요.";
            activeJobsBySourceImport.remove(job.sourceImportId, job.id);
            log.error("Saved verification export failed: exportId={}, sourceImportId={}",
                job.id, job.sourceImportId, exception);
        }
    }

    private ExportJob requireJob(UUID exportId) {
        ExportJob job = jobs.get(exportId);
        if (job == null) throw CustomException.of(VERIFICATION_EXPORT_NOT_FOUND);
        return job;
    }

    private synchronized void cleanupExpired() {
        Instant threshold = Instant.now().minus(JOB_TTL);
        jobs.values().removeIf(job -> {
            if (job.createdAt.isAfter(threshold)) return false;
            deleteQuietly(job.file);
            activeJobsBySourceImport.remove(job.sourceImportId, job.id);
            return true;
        });
    }

    @PreDestroy
    void cleanup() {
        jobs.values().forEach(job -> deleteQuietly(job.file));
        jobs.clear();
        activeJobsBySourceImport.clear();
    }

    private void deleteQuietly(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("Temporary verification export could not be deleted: {}", file, exception);
        }
    }

    private static final class ExportJob {
        private final UUID id;
        private final Long sourceImportId;
        private final Instant createdAt;
        private volatile String status = PROCESSING;
        private volatile String message;
        private volatile Path file;

        private ExportJob(UUID id, Long sourceImportId, Instant createdAt) {
            this.id = id;
            this.sourceImportId = sourceImportId;
            this.createdAt = createdAt;
        }
    }
}
