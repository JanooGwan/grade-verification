package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.STORED_VERIFICATION_JOB_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.STORED_VERIFICATION_QUEUE_FULL;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.dto.StoredVerificationJobResponse;
import com.jinhakapply.gradevalidation.transcript.dto.StoredVerificationPersistenceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

@Service
public class StoredVerificationJobService {
    private static final Logger log = LoggerFactory.getLogger(StoredVerificationJobService.class);
    private static final Duration JOB_TTL = Duration.ofHours(6);
    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";

    private final StoredTranscriptVerificationService verificationService;
    private final Executor executor;
    private final Map<UUID, VerificationJob> jobs = new ConcurrentHashMap<>();
    private final Map<VerificationScope, UUID> activeJobs = new ConcurrentHashMap<>();

    public StoredVerificationJobService(
        StoredTranscriptVerificationService verificationService,
        @Qualifier("storedVerificationExecutor") Executor executor
    ) {
        this.verificationService = verificationService;
        this.executor = executor;
    }

    public synchronized StoredVerificationJobResponse start(Long universityId, int admissionYear) {
        cleanupExpired();
        VerificationScope scope = new VerificationScope(universityId, admissionYear);
        UUID activeId = activeJobs.get(scope);
        VerificationJob active = activeId == null ? null : jobs.get(activeId);
        if (active != null && PROCESSING.equals(active.status)) {
            return response(active);
        }

        VerificationJob job = new VerificationJob(UUID.randomUUID(), scope, Instant.now());
        jobs.put(job.id, job);
        activeJobs.put(scope, job.id);
        try {
            executor.execute(() -> run(job));
        } catch (TaskRejectedException exception) {
            jobs.remove(job.id);
            activeJobs.remove(scope, job.id);
            throw CustomException.of(STORED_VERIFICATION_QUEUE_FULL);
        }
        return response(job);
    }

    public StoredVerificationJobResponse status(UUID jobId) {
        cleanupExpired();
        VerificationJob job = jobs.get(jobId);
        if (job == null) throw CustomException.of(STORED_VERIFICATION_JOB_NOT_FOUND);
        return response(job);
    }

    private void run(VerificationJob job) {
        try {
            job.result = verificationService.persist(job.scope.universityId, job.scope.admissionYear);
            job.status = COMPLETED;
            log.info("Stored transcript verification completed: jobId={}, universityId={}, admissionYear={}, savedResults={}",
                job.id, job.scope.universityId, job.scope.admissionYear, job.result.savedResults());
        } catch (CustomException exception) {
            job.message = exception.getFullMessage();
            job.status = FAILED;
            log.warn("Stored transcript verification rejected: jobId={}, universityId={}, admissionYear={}",
                job.id, job.scope.universityId, job.scope.admissionYear, exception);
        } catch (Exception exception) {
            job.message = "성적 검증 중 예상하지 못한 오류가 발생했습니다. 서버 로그를 확인해 주세요.";
            job.status = FAILED;
            log.error("Stored transcript verification failed: jobId={}, universityId={}, admissionYear={}",
                job.id, job.scope.universityId, job.scope.admissionYear, exception);
        } finally {
            job.completedAt = Instant.now();
            activeJobs.remove(job.scope, job.id);
        }
    }

    private synchronized void cleanupExpired() {
        Instant threshold = Instant.now().minus(JOB_TTL);
        jobs.values().removeIf(job -> {
            if (PROCESSING.equals(job.status) || job.completedAt == null || job.completedAt.isAfter(threshold)) {
                return false;
            }
            activeJobs.remove(job.scope, job.id);
            return true;
        });
    }

    private StoredVerificationJobResponse response(VerificationJob job) {
        return new StoredVerificationJobResponse(
            job.id,
            job.scope.universityId,
            job.scope.admissionYear,
            job.status,
            job.message,
            job.startedAt,
            job.completedAt,
            job.result
        );
    }

    private record VerificationScope(Long universityId, int admissionYear) {
    }

    private static final class VerificationJob {
        private final UUID id;
        private final VerificationScope scope;
        private final Instant startedAt;
        private volatile String status = PROCESSING;
        private volatile String message;
        private volatile Instant completedAt;
        private volatile StoredVerificationPersistenceResponse result;

        private VerificationJob(UUID id, VerificationScope scope, Instant startedAt) {
            this.id = id;
            this.scope = scope;
            this.startedAt = startedAt;
        }
    }
}
