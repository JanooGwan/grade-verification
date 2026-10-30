package com.jinhakapply.gradevalidation.operation.dto;

import java.time.LocalDateTime;

public record LightDataStatus(Long universityId, String universityCode, String universityName, boolean active,
    Integer admissionYear, boolean studentDataPresent, String latestImportStatus, LocalDateTime latestImportAt,
    boolean verificationDataPresent, LocalDateTime latestVerificationAt) {}
