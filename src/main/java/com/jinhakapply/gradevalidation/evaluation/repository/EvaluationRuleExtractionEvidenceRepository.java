package com.jinhakapply.gradevalidation.evaluation.repository;

import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleExtractionEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRuleExtractionEvidenceRepository
    extends JpaRepository<EvaluationRuleExtractionEvidence, Long> {

    List<EvaluationRuleExtractionEvidence> findAllByExtraction_IdOrderByPageNumberAscFieldKeyAsc(Long extractionId);
}
