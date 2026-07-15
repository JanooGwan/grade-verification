package com.jinhakapply.gradevalidation.transcript.repository;

public interface StudentCourseSummaryProjection {
    Long getStudentId();
    long getCourseCount();
    Double getAverageGrade();
}
