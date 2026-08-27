package com.jinhakapply.gradevalidation.admission.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.jinhakapply.gradevalidation.admission.domain.VerificationRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationRunRepository extends JpaRepository<VerificationRun, Long> {
    @EntityGraph(attributePaths = {"rule", "rule.university", "application", "application.recruitmentUnit"})
    List<VerificationRun> findTop50ByStudentIdOrderByCreatedAtDesc(Long studentId);

    @EntityGraph(attributePaths = {"student", "rule", "rule.university", "application", "application.recruitmentUnit"})
    Optional<VerificationRun> findOneById(Long id);

    boolean existsBySourceImport_Id(Long sourceImportId);

    @Query("SELECT DISTINCT run.sourceImport.id FROM VerificationRun run WHERE run.sourceImport.id IN :sourceImportIds")
    Set<Long> findSourceImportIdsWithResults(@Param("sourceImportIds") List<Long> sourceImportIds);
}
