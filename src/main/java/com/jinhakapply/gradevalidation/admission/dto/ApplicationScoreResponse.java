package com.jinhakapply.gradevalidation.admission.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreRun;
import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreStatus;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreResult;
import com.jinhakapply.gradevalidation.admission.domain.ScoreCalculationStep;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;

public record ApplicationScoreResponse(
    Long scoreRunId,
    LocalDateTime createdAt,
    Long applicationId,
    Long ruleId,
    int ruleVersion,
    String universityName,
    int admissionYear,
    String admissionTrackName,
    String recruitmentUnitName,
    EducationBackground educationBackground,
    ApplicationScoreStatus status,
    BigDecimal academicBaseScore,
    BigDecimal academicScore,
    Integer equivalentAbsenceDays,
    BigDecimal attendanceScore,
    BigDecimal additionalScore,
    BigDecimal schoolViolenceDeduction,
    BigDecimal quantitativeSubtotal,
    BigDecimal scoreAfterDeduction,
    BigDecimal finalScore,
    BigDecimal maximumQuantitativeScore,
    BigDecimal maximumTotalScore,
    List<String> pendingComponents,
    List<String> ineligibilityReasons,
    List<String> warnings,
    List<ScoreCalculationStep> calculationSteps,
    GradeVerificationResponse gradeVerification
) {
    public static ApplicationScoreResponse from(
        ApplicationScoreRun run,
        ApplicationScoreResult result,
        GradeVerificationResponse gradeVerification
    ) {
        var application = run.getApplication();
        var unit = application.getRecruitmentUnit();
        var track = unit.getAdmissionTrack();
        return new ApplicationScoreResponse(
            run.getId(), run.getCreatedAt(), application.getId(), run.getRule().getId(), run.getRuleVersion(),
            track.getUniversity().getName(), track.getAdmissionYear(), track.getName(), unit.getName(),
            run.getEducationBackground(), result.status(), result.academicBaseScore(), result.academicScore(),
            result.equivalentAbsenceDays(), result.attendanceScore(), result.additionalScore(),
            result.schoolViolenceDeduction(), result.quantitativeSubtotal(), result.scoreAfterDeduction(),
            result.finalScore(), result.maximumQuantitativeScore(), result.maximumTotalScore(),
            result.pendingComponents(), result.ineligibilityReasons(), result.warnings(), result.calculationSteps(),
            gradeVerification
        );
    }
}
