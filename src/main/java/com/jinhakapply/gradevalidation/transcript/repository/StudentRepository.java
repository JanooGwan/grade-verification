package com.jinhakapply.gradevalidation.transcript.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jinhakapply.gradevalidation.transcript.domain.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByUniversity_IdAndAdmissionYearAndApplicantNumber(
        Long universityId, int admissionYear, String applicantNumber
    );

    List<Student> findAllByUniversity_IdAndAdmissionYearAndApplicantNumberIn(
        Long universityId, int admissionYear, Collection<String> applicantNumbers
    );

    List<Student> findAllByUniversity_IdAndAdmissionYear(Long universityId, int admissionYear);

    @Query("""
        SELECT s
        FROM Student s
        WHERE s.university.id = :universityId
          AND s.admissionYear = :admissionYear
          AND (
            :keyword IS NULL
            OR LOWER(s.applicantNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(COALESCE(s.highSchoolName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        """)
    Page<Student> search(
        @Param("universityId") Long universityId,
        @Param("admissionYear") int admissionYear,
        @Param("keyword") String keyword,
        Pageable pageable
    );
}
