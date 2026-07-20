package com.jinhakapply.gradevalidation.evaluation.repository;

import java.util.Optional;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleExtraction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface EvaluationRuleExtractionRepository extends JpaRepository<EvaluationRuleExtraction, Long> {

    @EntityGraph(attributePaths = "university")
    Optional<EvaluationRuleExtraction> findOneById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "university")
    @Query("SELECT extraction FROM EvaluationRuleExtraction extraction WHERE extraction.id = :id")
    Optional<EvaluationRuleExtraction> findOneByIdForUpdate(Long id);

    Optional<EvaluationRuleExtraction> findFirstByUniversityIdAndAdmissionYearAndFileSha256(
        Long universityId, int admissionYear, String fileSha256
    );

    @EntityGraph(attributePaths = "university")
    List<EvaluationRuleExtraction> findTop100ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "university")
    List<EvaluationRuleExtraction> findTop100ByUniversityIdAndAdmissionYearOrderByCreatedAtDesc(
        Long universityId, int admissionYear
    );

    @EntityGraph(attributePaths = "university")
    List<EvaluationRuleExtraction> findTop100ByUniversityIdOrderByCreatedAtDesc(Long universityId);

    @EntityGraph(attributePaths = "university")
    List<EvaluationRuleExtraction> findTop100ByAdmissionYearOrderByCreatedAtDesc(int admissionYear);
}
