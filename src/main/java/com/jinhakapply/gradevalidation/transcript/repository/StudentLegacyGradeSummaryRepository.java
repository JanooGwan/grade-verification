package com.jinhakapply.gradevalidation.transcript.repository;

import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.StudentLegacyGradeSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentLegacyGradeSummaryRepository extends JpaRepository<StudentLegacyGradeSummary, Long> {
    List<StudentLegacyGradeSummary> findAllByStudent_IdOrderBySchoolYearAscSemesterAsc(Long studentId);
    void deleteAllByStudent_Id(Long studentId);
}
