package com.jinhakapply.gradevalidation.admission.repository;

import java.util.List;
import java.util.Optional;

import com.jinhakapply.gradevalidation.admission.domain.VerificationRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationRunRepository extends JpaRepository<VerificationRun, Long> {
    @EntityGraph(attributePaths = {"rule", "rule.university", "application", "application.recruitmentUnit"})
    List<VerificationRun> findTop50ByStudentIdOrderByCreatedAtDesc(Long studentId);

    @EntityGraph(attributePaths = {"student", "rule", "rule.university", "application", "application.recruitmentUnit"})
    Optional<VerificationRun> findOneById(Long id);
}
