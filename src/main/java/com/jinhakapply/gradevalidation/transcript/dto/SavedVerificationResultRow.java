package com.jinhakapply.gradevalidation.transcript.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SavedVerificationResultRow(
    Long verificationRunId,
    Long studentId,
    String applicantNumber,
    String studentName,
    String admissionTrackName,
    String recruitmentUnitName,
    String ruleName,
    int ruleVersion,
    BigDecimal finalScore,
    BigDecimal averageGrade,
    int includedCourseCount,
    int excludedCourseCount,
    LocalDateTime savedAt
) {}
