package com.jinhakapply.gradevalidation.admission.repository;

import java.util.List;
import java.util.Optional;

import com.jinhakapply.gradevalidation.admission.domain.StudentApplication;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentApplicationRepository extends JpaRepository<StudentApplication, Long> {
    boolean existsByStudentIdAndRecruitmentUnitId(Long studentId, Long recruitmentUnitId);

    @EntityGraph(attributePaths = {
        "student", "recruitmentUnit", "recruitmentUnit.admissionTrack",
        "recruitmentUnit.admissionTrack.university"
    })
    List<StudentApplication> findAllByStudentIdOrderByCreatedAtDesc(Long studentId);

    @EntityGraph(attributePaths = {
        "student", "recruitmentUnit", "recruitmentUnit.admissionTrack",
        "recruitmentUnit.admissionTrack.university"
    })
    Optional<StudentApplication> findOneById(Long id);
}
