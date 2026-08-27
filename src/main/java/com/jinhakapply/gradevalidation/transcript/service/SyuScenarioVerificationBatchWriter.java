package com.jinhakapply.gradevalidation.transcript.service;

import java.util.List;

import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository;
import com.jinhakapply.gradevalidation.admission.repository.BatchVerificationRunRepository.ScenarioVerificationRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class SyuScenarioVerificationBatchWriter {

    private final BatchVerificationRunRepository verificationRunRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public int deleteAll(Long sourceImportId) {
        return verificationRunRepository.deleteAllBySourceImportId(sourceImportId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int insert(Long sourceImportId, List<ScenarioVerificationRow> rows) {
        return verificationRunRepository.insertScenarios(sourceImportId, rows);
    }
}
