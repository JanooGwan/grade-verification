package com.jinhakapply.gradevalidation.operation.service;

import com.jinhakapply.gradevalidation.operation.dto.ActiveJobsResponse;
import com.jinhakapply.gradevalidation.transcript.service.SavedVerificationExportService;
import com.jinhakapply.gradevalidation.transcript.service.StoredVerificationJobService;
import com.jinhakapply.gradevalidation.transcript.service.SourceImportJobTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class ActiveJobsService {
    private final StoredVerificationJobService verificationJobs;
    private final SavedVerificationExportService exportJobs;
    private final JdbcTemplate jdbc;
    private final SourceImportJobTracker importJobs;

    @Transactional(readOnly = true)
    public ActiveJobsResponse findActive() {
        var exports = exportJobs.activeJobs();
        var scopes = new HashMap<Long, long[]>();
        if (!exports.isEmpty()) {
            var ids = exports.stream().map(SavedVerificationExportService.ActiveExport::sourceImportId).distinct().toList();
            String markers = String.join(",", Collections.nCopies(ids.size(), "?"));
            jdbc.query("SELECT id, university_id, admission_year FROM student_transcript_import WHERE id IN (" + markers + ")",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> scopes.put(rs.getLong("id"),
                    new long[]{rs.getLong("university_id"), rs.getInt("admission_year")}), ids.toArray());
        }
        var exportViews = exports.stream().map(job -> {
            long[] scope = scopes.get(job.sourceImportId());
            return new ActiveJobsResponse.ActiveExport(job.exportId(), job.sourceImportId(),
                scope == null ? null : scope[0], scope == null ? null : (int) scope[1], job.startedAt());
        }).toList();
        return new ActiveJobsResponse(verificationJobs.activeJobs(), exportViews, importJobs.activeJobs());
    }
}
