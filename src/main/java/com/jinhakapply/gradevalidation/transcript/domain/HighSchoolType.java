package com.jinhakapply.gradevalidation.transcript.domain;

public enum HighSchoolType {
    GENERAL,
    SPECIALIZED,
    COMPREHENSIVE_VOCATIONAL,
    LIFELONG_EDUCATION_FACILITY;

    public boolean usesHanshinAllOrdinaryCoursesPolicy() {
        return this != GENERAL;
    }
}
