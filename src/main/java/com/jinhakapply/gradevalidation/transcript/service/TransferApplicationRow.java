package com.jinhakapply.gradevalidation.transcript.service;

record TransferApplicationRow(
    int rowNumber,
    int admissionYear,
    String applicantNumber,
    String admissionTrackCode,
    String admissionTrackName,
    String recruitmentUnitCode,
    String recruitmentUnitName,
    Integer graduationYear
) {}
