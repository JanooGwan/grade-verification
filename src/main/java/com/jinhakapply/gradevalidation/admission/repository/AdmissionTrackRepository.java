package com.jinhakapply.gradevalidation.admission.repository;

import java.util.List;

import com.jinhakapply.gradevalidation.admission.domain.AdmissionTrack;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionTrackRepository extends JpaRepository<AdmissionTrack, Long> {
    boolean existsByUniversityIdAndAdmissionYearAndName(Long universityId, int admissionYear, String name);

    @EntityGraph(attributePaths = "university")
    List<AdmissionTrack> findAllByUniversityIdAndAdmissionYearOrderByNameAsc(Long universityId, int admissionYear);
}
