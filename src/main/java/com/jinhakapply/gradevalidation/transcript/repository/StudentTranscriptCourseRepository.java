package com.jinhakapply.gradevalidation.transcript.repository;

import java.util.List;
import java.util.Optional;

import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentTranscriptCourseRepository extends JpaRepository<StudentTranscriptCourse, Long> {

    Optional<StudentTranscriptCourse> findByStudent_IdAndSchoolYearAndSemesterAndSubjectCategoryAndCourseName(
        Long studentId,
        int schoolYear,
        int semester,
        SubjectCategory subjectCategory,
        String courseName
    );

    List<StudentTranscriptCourse> findAllByStudent_IdOrderBySchoolYearAscSemesterAscCourseNameAsc(Long studentId);

    List<StudentTranscriptCourse> findAllByStudent_IdIn(List<Long> studentIds);

    Optional<StudentTranscriptCourse> findByIdAndStudent_Id(Long id, Long studentId);

    @Query("""
        SELECT c.student.id AS studentId,
               COUNT(c.id) AS courseCount,
               AVG(c.grade) AS averageGrade
        FROM StudentTranscriptCourse c
        WHERE c.student.id IN :studentIds
        GROUP BY c.student.id
        """)
    List<StudentCourseSummaryProjection> summarizeByStudentIds(@Param("studentIds") List<Long> studentIds);
}
