package com.jinhakapply.gradevalidation.transcript.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Current-process jobs only; stale DB import rows are not presented as running. */
@Component
public class SourceImportJobTracker {
    public record ActiveImport(Long importId, Long universityId, int admissionYear, String status, Instant startedAt) {}
    private final ConcurrentHashMap<Long, ActiveImport> active = new ConcurrentHashMap<>();

    public void register(Long id, Long universityId, int admissionYear) {
        active.put(id, new ActiveImport(id, universityId, admissionYear, "QUEUED", Instant.now()));
    }
    public void started(Long id) {
        active.computeIfPresent(id, (key, item) -> new ActiveImport(item.importId(), item.universityId(),
            item.admissionYear(), "PROCESSING", item.startedAt()));
    }
    public void finished(Long id) { active.remove(id); }
    public List<ActiveImport> activeJobs() {
        return active.values().stream().sorted(Comparator.comparing(ActiveImport::startedAt)).toList();
    }
}
