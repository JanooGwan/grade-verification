package com.jinhakapply.gradevalidation.admission.repository;

import java.util.List;

import com.jinhakapply.gradevalidation.admission.domain.RecruitmentUnit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruitmentUnitRepository extends JpaRepository<RecruitmentUnit, Long> {
    boolean existsByAdmissionTrackIdAndName(Long trackId, String name);

    boolean existsByAdmissionTrackIdAndCode(Long trackId, String code);

    boolean existsByAdmissionTrackIdAndCodeAndIdNot(Long trackId, String code, Long id);

    @EntityGraph(attributePaths = {"admissionTrack", "admissionTrack.university"})
    List<RecruitmentUnit> findAllByAdmissionTrackIdOrderByNameAsc(Long trackId);

    @EntityGraph(attributePaths = {"admissionTrack", "admissionTrack.university"})
    List<RecruitmentUnit> findAllByAdmissionTrackIdInOrderByNameAsc(List<Long> trackIds);
}
