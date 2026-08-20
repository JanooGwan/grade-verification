package com.jinhakapply.gradevalidation.transcript.service;

import java.math.BigDecimal;

import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse.CourseCalculation;

record SyuScenarioExportSummary(
    BigDecimal convertedScoreTimesCreditsSum,
    BigDecimal totalIncludedCredits,
    BigDecimal semester11,
    BigDecimal semester12,
    BigDecimal semester21,
    BigDecimal semester22,
    BigDecimal semester31,
    BigDecimal semester32,
    BigDecimal baseScore
) {
    static SyuScenarioExportSummary from(GradeVerificationResponse verification) {
        BigDecimal[][] weightedScoreSums = sums();
        BigDecimal[][] appliedWeightSums = sums();
        if (verification.calculations() != null) {
            for (CourseCalculation calculation : verification.calculations()) {
                if (!calculation.included()
                    || calculation.schoolYear() < 1 || calculation.schoolYear() > 3
                    || calculation.semester() < 1 || calculation.semester() > 2) {
                    continue;
                }
                int year = calculation.schoolYear() - 1;
                int semester = calculation.semester() - 1;
                weightedScoreSums[year][semester] = weightedScoreSums[year][semester]
                    .add(calculation.weightedScore());
                appliedWeightSums[year][semester] = appliedWeightSums[year][semester]
                    .add(calculation.appliedWeight());
            }
        }
        return new SyuScenarioExportSummary(
            verification.calculationSummary().convertedScoreTimesCreditsSum(),
            verification.calculationSummary().totalIncludedCredits(),
            semester(verification, weightedScoreSums, appliedWeightSums, 1, 1),
            semester(verification, weightedScoreSums, appliedWeightSums, 1, 2),
            semester(verification, weightedScoreSums, appliedWeightSums, 2, 1),
            semester(verification, weightedScoreSums, appliedWeightSums, 2, 2),
            semester(verification, weightedScoreSums, appliedWeightSums, 3, 1),
            semester(verification, weightedScoreSums, appliedWeightSums, 3, 2),
            verification.baseScore()
        );
    }

    private static BigDecimal[][] sums() {
        return new BigDecimal[][] {
            {BigDecimal.ZERO, BigDecimal.ZERO},
            {BigDecimal.ZERO, BigDecimal.ZERO},
            {BigDecimal.ZERO, BigDecimal.ZERO}
        };
    }

    private static BigDecimal semester(
        GradeVerificationResponse verification,
        BigDecimal[][] weightedScoreSums,
        BigDecimal[][] appliedWeightSums,
        int schoolYear,
        int semester
    ) {
        return SyuImportScoreExcelWriter.semesterIntermediate(
            weightedScoreSums[schoolYear - 1][semester - 1],
            appliedWeightSums[schoolYear - 1][semester - 1],
            verification.calculationSummary().intermediateScale(),
            verification.calculationSummary().intermediateRounding()
        );
    }
}
