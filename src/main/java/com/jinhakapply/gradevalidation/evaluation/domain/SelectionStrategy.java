package com.jinhakapply.gradevalidation.evaluation.domain;

public enum SelectionStrategy {
    ALL_COURSES,
    TOP_N_COURSES,
    TOP_N_COURSES_PER_SUBJECT,
    CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N,
    TOP_N_SEMESTERS,
    TOP_N_SUBJECTS,
    BEST_SEMESTER_PER_GRADE
}
