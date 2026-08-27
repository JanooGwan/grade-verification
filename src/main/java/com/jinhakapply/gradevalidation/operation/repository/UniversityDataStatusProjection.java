package com.jinhakapply.gradevalidation.operation.repository;

import java.time.LocalDateTime;

public record UniversityDataStatusProjection(
    Long universityId,
    String universityCode,
    String universityName,
    boolean active,
    Integer admissionYear,
    long studentCount,
    long transcriptCourseCount,
    long applicationCount,
    String latestImportStatus,
    String latestImportFileName,
    LocalDateTime latestImportAt,
    long verificationResultCount,
    LocalDateTime latestVerificationAt
) {}
