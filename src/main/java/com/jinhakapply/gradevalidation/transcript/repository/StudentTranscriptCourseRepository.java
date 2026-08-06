package com.jinhakapply.gradevalidation.transcript.repository;

import java.util.List;
import java.util.Optional;

import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentTranscriptCourseRepository extends JpaRepository<StudentTranscriptCourse, Long> {

    Optional<StudentTranscriptCourse> findByStudent_IdAndSchoolYearAndSemesterAndCourseNameNormalized(
        Long studentId,
        int schoolYear,
        int semester,
        String courseNameNormalized
    );

    List<StudentTranscriptCourse> findAllByStudent_IdOrderBySchoolYearAscSemesterAscCourseNameAsc(Long studentId);

    List<StudentTranscriptCourse> findAllByStudent_IdIn(List<Long> studentIds);

    List<Long> findDistinctStudentIdsBySourceImport_Id(Long sourceImportId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM StudentTranscriptCourse course WHERE course.sourceImport.id = :sourceImportId")
    int deleteAllBySourceImportId(@Param("sourceImportId") Long sourceImportId);

    @Query("""
        SELECT DISTINCT course.student.id
        FROM StudentTranscriptCourse course
        WHERE course.student.university.id = :universityId
          AND course.student.admissionYear = :admissionYear
          AND course.sourceFileName = :sourceFileName
        """)
    List<Long> findStudentIdsByImportSource(
        @Param("universityId") Long universityId,
        @Param("admissionYear") int admissionYear,
        @Param("sourceFileName") String sourceFileName
    );

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM StudentTranscriptCourse course WHERE course.student.id IN :studentIds")
    int deleteAllByStudentIds(@Param("studentIds") List<Long> studentIds);

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
