package com.jinhakapply.gradevalidation.transcript.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
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
    List<SubjectDomainScore> subjectDomains,
    BigDecimal baseScore
) {
    String domainNames() {
        if (subjectDomains == null || subjectDomains.isEmpty()) return "";
        return subjectDomains.stream().map(SubjectDomainScore::domainName)
            .collect(java.util.stream.Collectors.joining(", "));
    }

    SubjectDomainScore domain(int index) {
        return subjectDomains == null || index < 0 || index >= subjectDomains.size()
            ? null : subjectDomains.get(index);
    }

    static SyuScenarioExportSummary from(GradeVerificationResponse verification) {
        Map<SubjectDomain, BigDecimal> weightedScoreSums = new EnumMap<>(SubjectDomain.class);
        Map<SubjectDomain, BigDecimal> appliedWeightSums = new EnumMap<>(SubjectDomain.class);
        if (verification.calculations() != null) {
            for (CourseCalculation calculation : verification.calculations()) {
                SubjectDomain domain = domain(calculation.appliedSubjectCategory());
                if (!calculation.included() || domain == null) continue;
                weightedScoreSums.merge(domain, calculation.weightedScore(), BigDecimal::add);
                appliedWeightSums.merge(domain, calculation.appliedWeight(), BigDecimal::add);
            }
        }
        return new SyuScenarioExportSummary(
            verification.calculationSummary().convertedScoreTimesCreditsSum(),
            verification.calculationSummary().totalIncludedCredits(),
            null, null, null, null, null, null,
            domainScores(verification, weightedScoreSums, appliedWeightSums),
            verification.baseScore()
        );
    }

    private static List<SubjectDomainScore> domainScores(
        GradeVerificationResponse verification,
        Map<SubjectDomain, BigDecimal> weightedScoreSums,
        Map<SubjectDomain, BigDecimal> appliedWeightSums
    ) {
        List<DomainScore> scores = new ArrayList<>();
        for (SubjectDomain domain : SubjectDomain.values()) {
            BigDecimal appliedWeight = appliedWeightSums.getOrDefault(domain, BigDecimal.ZERO);
            BigDecimal score = SyuImportScoreExcelWriter.semesterIntermediate(
                weightedScoreSums.getOrDefault(domain, BigDecimal.ZERO),
                appliedWeight,
                verification.calculationSummary().intermediateScale(),
                verification.calculationSummary().intermediateRounding()
            );
            if (score != null) scores.add(new DomainScore(domain, score));
        }
        Comparator<DomainScore> order = verification.selectionStrategy() == SelectionStrategy.TOP_N_SUBJECTS
            ? Comparator.comparing(DomainScore::score).reversed()
                .thenComparingInt(value -> value.domain().selectionPriority)
            : Comparator.comparingInt(value -> value.domain().displayOrder);
        scores.sort(order);

        List<SubjectDomainScore> results = new ArrayList<>(scores.size());
        for (int index = 0; index < scores.size(); index++) {
            DomainScore score = scores.get(index);
            results.add(new SubjectDomainScore(index + 1, score.domain().label, score.score()));
        }
        return List.copyOf(results);
    }

    private static SubjectDomain domain(SubjectCategory category) {
        if (category == null) return null;
        return switch (category) {
            case KOREAN -> SubjectDomain.KOREAN;
            case ENGLISH -> SubjectDomain.ENGLISH;
            case MATH -> SubjectDomain.MATH;
            case SOCIAL, SCIENCE -> SubjectDomain.INQUIRY;
            case OTHER -> null;
        };
    }

    record SubjectDomainScore(int selectionOrder, String domainName, BigDecimal score) {}

    private record DomainScore(SubjectDomain domain, BigDecimal score) {}

    private enum SubjectDomain {
        KOREAN("국어", 1, 1),
        ENGLISH("영어", 2, 3),
        MATH("수학", 3, 2),
        INQUIRY("탐구", 4, 4);

        private final String label;
        private final int displayOrder;
        private final int selectionPriority;

        SubjectDomain(String label, int displayOrder, int selectionPriority) {
            this.label = label;
            this.displayOrder = displayOrder;
            this.selectionPriority = selectionPriority;
        }
    }
}
