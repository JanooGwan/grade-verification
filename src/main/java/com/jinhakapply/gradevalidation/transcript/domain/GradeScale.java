package com.jinhakapply.gradevalidation.transcript.domain;

public enum GradeScale {
    NINE_LEVEL(9),
    FIVE_LEVEL(5),
    LEGACY(9);

    private final int maximumGrade;

    GradeScale(int maximumGrade) {
        this.maximumGrade = maximumGrade;
    }

    public int maximumGrade() {
        return maximumGrade;
    }
}

