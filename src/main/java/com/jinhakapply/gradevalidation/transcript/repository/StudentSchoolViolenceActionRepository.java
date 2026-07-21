package com.jinhakapply.gradevalidation.transcript.repository;

import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.StudentSchoolViolenceAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentSchoolViolenceActionRepository extends JpaRepository<StudentSchoolViolenceAction, Long> {
    List<StudentSchoolViolenceAction> findAllByStudent_IdOrderBySchoolYearAscActionNumberAsc(Long studentId);
    void deleteAllByStudent_Id(Long studentId);
}
