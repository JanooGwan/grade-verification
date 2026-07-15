package com.jinhakapply.gradevalidation.evaluation.repository;

import java.util.List;
import java.util.Optional;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRuleRepository extends JpaRepository<EvaluationRule, Long> {
    long countByStatus(EvaluationRuleStatus status);
    boolean existsByUniversityIdAndAdmissionYearAndAdmissionTypeAndRecruitmentUnitAndVersion(
        Long universityId,
        int admissionYear,
        String admissionType,
        String recruitmentUnit,
        int version
    );

    @EntityGraph(attributePaths = "university")
    List<EvaluationRule> findAllByOrderByAdmissionYearDescNameAsc();

    @EntityGraph(attributePaths = "university")
    List<EvaluationRule> findAllByStatusOrderByAdmissionYearDescNameAsc(EvaluationRuleStatus status);

    List<EvaluationRule> findAllByUniversityIdAndAdmissionYearAndAdmissionTypeAndRecruitmentUnitAndStatus(
        Long universityId,
        int admissionYear,
        String admissionType,
        String recruitmentUnit,
        EvaluationRuleStatus status
    );

    @EntityGraph(attributePaths = "university")
    List<EvaluationRule> findAllByUniversityIdAndAdmissionYearAndStatus(
        Long universityId,
        int admissionYear,
        EvaluationRuleStatus status
    );

    @EntityGraph(attributePaths = "university")
    Optional<EvaluationRule> findOneById(Long id);
}
