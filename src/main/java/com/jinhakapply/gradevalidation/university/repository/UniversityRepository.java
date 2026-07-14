package com.jinhakapply.gradevalidation.university.repository;

import com.jinhakapply.gradevalidation.university.domain.University;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityRepository extends JpaRepository<University, Long> {

	boolean existsByCodeIgnoreCase(String code);
}
