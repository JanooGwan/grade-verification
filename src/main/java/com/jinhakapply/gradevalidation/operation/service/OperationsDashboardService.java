package com.jinhakapply.gradevalidation.operation.service;

import com.jinhakapply.gradevalidation.admission.repository.StudentApplicationRepository;
import com.jinhakapply.gradevalidation.admission.repository.VerificationRunRepository;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleExtractionRepository;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.operation.dto.OperationsDashboardResponse;
import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptCourseRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperationsDashboardService {
    private final UniversityRepository universityRepository;
    private final StudentRepository studentRepository;
    private final StudentTranscriptCourseRepository courseRepository;
    private final StudentTranscriptImportRepository importRepository;
    private final StudentApplicationRepository applicationRepository;
    private final VerificationRunRepository verificationRunRepository;
    private final EvaluationRuleExtractionRepository extractionRepository;
    private final EvaluationRuleRepository ruleRepository;
    private final OperationalMetrics metrics;

    public OperationsDashboardResponse getDashboard() {
        return new OperationsDashboardResponse(
            universityRepository.count(), studentRepository.count(), courseRepository.count(), importRepository.count(),
            applicationRepository.count(), verificationRunRepository.count(), extractionRepository.count(),
            new OperationsDashboardResponse.RuleCounts(
                ruleRepository.countByStatus(EvaluationRuleStatus.DRAFT),
                ruleRepository.countByStatus(EvaluationRuleStatus.VERIFIED),
                ruleRepository.countByStatus(EvaluationRuleStatus.PUBLISHED),
                ruleRepository.countByStatus(EvaluationRuleStatus.RETIRED)
            ),
            metrics.snapshot()
        );
    }
}
