package com.jinhakapply.gradevalidation.evaluation.repository;

import java.util.Optional;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleExtraction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRuleExtractionRepository extends JpaRepository<EvaluationRuleExtraction, Long> {

    @EntityGraph(attributePaths = "university")
    Optional<EvaluationRuleExtraction> findOneById(Long id);

    Optional<EvaluationRuleExtraction> findFirstByUniversityIdAndAdmissionYearAndFileSha256(
        Long universityId, int admissionYear, String fileSha256
    );

    @EntityGraph(attributePaths = "university")
    List<EvaluationRuleExtraction> findTop100ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "university")
    List<EvaluationRuleExtraction> findTop100ByUniversityIdAndAdmissionYearOrderByCreatedAtDesc(
        Long universityId, int admissionYear
    );
}
