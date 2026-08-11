package com.jinhakapply.gradevalidation.transcript.service;

record TransferApplicationRow(
    Long applicationId,
    int rowNumber,
    int admissionYear,
    String applicantNumber,
    String admissionTrackCode,
    String admissionTrackName,
    String recruitmentUnitCode,
    String recruitmentUnitName,
    Integer graduationYear,
    String recruitmentPeriodName
) {
    TransferApplicationRow(
        int rowNumber,
        int admissionYear,
        String applicantNumber,
        String admissionTrackCode,
        String admissionTrackName,
        String recruitmentUnitCode,
        String recruitmentUnitName,
        Integer graduationYear
    ) {
        this(
            null, rowNumber, admissionYear, applicantNumber, admissionTrackCode, admissionTrackName,
            recruitmentUnitCode, recruitmentUnitName, graduationYear, null
        );
    }

    TransferApplicationRow(
        int rowNumber,
        int admissionYear,
        String applicantNumber,
        String admissionTrackCode,
        String admissionTrackName,
        String recruitmentUnitCode,
        String recruitmentUnitName,
        Integer graduationYear,
        String recruitmentPeriodName
    ) {
        this(
            null, rowNumber, admissionYear, applicantNumber, admissionTrackCode, admissionTrackName,
            recruitmentUnitCode, recruitmentUnitName, graduationYear, recruitmentPeriodName
        );
    }
}
