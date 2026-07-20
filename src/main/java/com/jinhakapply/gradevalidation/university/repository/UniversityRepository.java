package com.jinhakapply.gradevalidation.university.repository;

import com.jinhakapply.gradevalidation.university.domain.University;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface UniversityRepository extends JpaRepository<University, Long> {

	boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT university FROM University university WHERE university.id = :id")
    Optional<University> findByIdForUpdate(Long id);
}
