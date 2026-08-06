package com.jinhakapply.gradevalidation.admission.repository;

import java.util.List;
import java.util.Optional;

import com.jinhakapply.gradevalidation.admission.domain.StudentApplication;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentApplicationRepository extends JpaRepository<StudentApplication, Long> {
    boolean existsByStudentIdAndRecruitmentUnitId(Long studentId, Long recruitmentUnitId);

    @EntityGraph(attributePaths = {
        "student", "recruitmentUnit", "recruitmentUnit.admissionTrack",
        "recruitmentUnit.admissionTrack.university"
    })
    List<StudentApplication> findAllByStudentIdOrderByCreatedAtDesc(Long studentId);

    @EntityGraph(attributePaths = {"student", "recruitmentUnit"})
    List<StudentApplication> findAllByStudent_IdIn(List<Long> studentIds);

    @EntityGraph(attributePaths = {"student", "recruitmentUnit", "recruitmentUnit.admissionTrack"})
    List<StudentApplication> findAllBySourceImport_Id(Long sourceImportId);

    @Query("""
        SELECT application
        FROM StudentApplication application
        JOIN FETCH application.student student
        JOIN FETCH application.recruitmentUnit unit
        JOIN FETCH unit.admissionTrack track
        WHERE student.id IN :studentIds
          AND track.university.id = :universityId
          AND track.admissionYear = :admissionYear
        """)
    List<StudentApplication> findAllForImportScope(
        @Param("studentIds") List<Long> studentIds,
        @Param("universityId") Long universityId,
        @Param("admissionYear") int admissionYear
    );

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM StudentApplication application WHERE application.sourceImport.id = :sourceImportId")
    int deleteAllBySourceImportId(@Param("sourceImportId") Long sourceImportId);

    @EntityGraph(attributePaths = {
        "student", "recruitmentUnit", "recruitmentUnit.admissionTrack",
        "recruitmentUnit.admissionTrack.university"
    })
    Optional<StudentApplication> findOneById(Long id);
}
