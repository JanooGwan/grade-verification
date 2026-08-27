package com.jinhakapply.gradevalidation.transcript.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository.ScenarioVerificationRow;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.dto.StoredVerificationPersistenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
class SyuScenarioVerificationPersistenceService {

    private static final int INSERT_BATCH_SIZE = 200;

    private final SyuImportScoreExcelWriter scoreExcelWriter;
    private final SyuScenarioVerificationBatchWriter batchWriter;
    private final ObjectMapper objectMapper;

    @Transactional
    public StoredVerificationPersistenceResponse persist(
        StudentTranscriptImport transcriptImport,
        List<EvaluationRule> rules
    ) {
        Long sourceImportId = transcriptImport.getId();
        int replacedResults = batchWriter.deleteAll(sourceImportId);
        List<ScenarioVerificationRow> insertBuffer = new ArrayList<>(INSERT_BATCH_SIZE);
        int[] savedResults = {0};

        SyuImportScoreExcelWriter.ScenarioVerificationSummary summary = scoreExcelWriter.verifyScenarios(
            transcriptImport,
            rules,
            (studentId, result) -> {
                insertBuffer.add(new ScenarioVerificationRow(
                    studentId,
                    result,
                    objectMapper.writeValueAsString(result),
                    objectMapper.writeValueAsString(SyuScenarioExportSummary.from(result))
                ));
                if (insertBuffer.size() >= INSERT_BATCH_SIZE) {
                    savedResults[0] += flush(sourceImportId, insertBuffer);
                }
            }
        );
        savedResults[0] += flush(sourceImportId, insertBuffer);
        return new StoredVerificationPersistenceResponse(
            sourceImportId,
            summary.totalScenarios(),
            savedResults[0],
            summary.failedScenarios(),
            replacedResults,
            LocalDateTime.now()
        );
    }

    private int flush(Long sourceImportId, List<ScenarioVerificationRow> buffer) {
        if (buffer.isEmpty()) return 0;
        int inserted = batchWriter.insert(sourceImportId, List.copyOf(buffer));
        buffer.clear();
        return inserted;
    }
}
