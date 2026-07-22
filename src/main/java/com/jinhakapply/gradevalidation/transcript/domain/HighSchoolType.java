package com.jinhakapply.gradevalidation.transcript.domain;

public enum HighSchoolType {
    GENERAL,
    SPECIALIZED,
    COMPREHENSIVE_VOCATIONAL,
    LIFELONG_EDUCATION_FACILITY,
    TWO_YEAR;

    public boolean usesHanshinAllOrdinaryCoursesPolicy() {
        return this == SPECIALIZED || this == COMPREHENSIVE_VOCATIONAL
            || this == LIFELONG_EDUCATION_FACILITY;
    }
}
