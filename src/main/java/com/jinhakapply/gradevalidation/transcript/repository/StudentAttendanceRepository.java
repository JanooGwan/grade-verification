package com.jinhakapply.gradevalidation.transcript.repository;

import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.StudentAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAttendanceRepository extends JpaRepository<StudentAttendance, Long> {
    List<StudentAttendance> findAllByStudent_IdOrderBySchoolYearAsc(Long studentId);
    void deleteAllByStudent_Id(Long studentId);
}
