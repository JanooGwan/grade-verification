package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SourceImportJobTrackerTest {
    @Test
    void tracksQueuedAndRunningJobsUntilTheyActuallyFinish() {
        var tracker = new SourceImportJobTracker();
        tracker.register(11L, 3L, 2027);
        assertThat(tracker.activeJobs()).singleElement().satisfies(job -> {
            assertThat(job.status()).isEqualTo("QUEUED");
            assertThat(job.universityId()).isEqualTo(3L);
        });
        var queuedAt = tracker.activeJobs().getFirst().startedAt();
        tracker.started(11L);
        assertThat(tracker.activeJobs()).singleElement().satisfies(job -> {
            assertThat(job.status()).isEqualTo("PROCESSING");
            assertThat(job.startedAt()).isEqualTo(queuedAt);
        });
        tracker.finished(11L);
        assertThat(tracker.activeJobs()).isEmpty();
        tracker.started(11L);
        assertThat(tracker.activeJobs()).isEmpty();
    }
}
