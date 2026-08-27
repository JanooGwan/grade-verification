package com.jinhakapply.gradevalidation.transcript.repository;

import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.StudentGedSubjectScore;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentGedSubjectScoreRepository extends JpaRepository<StudentGedSubjectScore, Long> {
    List<StudentGedSubjectScore> findAllByStudent_IdOrderBySubjectTypeAscSubjectNameAsc(Long studentId);
    void deleteAllByStudent_Id(Long studentId);
}

