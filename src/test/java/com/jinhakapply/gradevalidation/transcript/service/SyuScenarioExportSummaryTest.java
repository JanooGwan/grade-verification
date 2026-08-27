package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse.CalculationSummary;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse.CourseCalculation;
import org.junit.jupiter.api.Test;

class SyuScenarioExportSummaryTest {

    @Test
    void exposesOnlySelectedTopTwoDomainsInScoreOrder() {
        GradeVerificationResponse verification = verification(
            SelectionStrategy.TOP_N_SUBJECTS,
            calculation(SubjectCategory.KOREAN, true, "294", "3"),
            calculation(SubjectCategory.ENGLISH, true, "396", "4"),
            calculation(SubjectCategory.MATH, false, "0", "2"),
            calculation(SubjectCategory.SOCIAL, false, "0", "2")
        );

        SyuScenarioExportSummary summary = SyuScenarioExportSummary.from(verification);

        assertThat(summary.domainNames()).isEqualTo("영어, 국어");
        assertThat(summary.subjectDomains())
            .extracting(SyuScenarioExportSummary.SubjectDomainScore::domainName)
            .containsExactly("영어", "국어");
        assertThat(summary.subjectDomains())
            .extracting(SyuScenarioExportSummary.SubjectDomainScore::score)
            .containsExactly(new BigDecimal("99.0000"), new BigDecimal("98.0000"));
    }

    @Test
    void specializedScenarioShowsKoreanEnglishAndMathWithoutInquiry() {
        GradeVerificationResponse verification = verification(
            SelectionStrategy.ALL_COURSES,
            calculation(SubjectCategory.KOREAN, true, "294", "3"),
            calculation(SubjectCategory.ENGLISH, true, "396", "4"),
            calculation(SubjectCategory.MATH, true, "194", "2"),
            calculation(SubjectCategory.SOCIAL, false, "0", "2"),
            calculation(SubjectCategory.SCIENCE, false, "0", "3")
        );

        SyuScenarioExportSummary summary = SyuScenarioExportSummary.from(verification);

        assertThat(summary.domainNames()).isEqualTo("국어, 영어, 수학");
        assertThat(summary.subjectDomains())
            .extracting(SyuScenarioExportSummary.SubjectDomainScore::domainName)
            .containsExactly("국어", "영어", "수학")
            .doesNotContain("탐구");
    }

    @Test
    void combinesSocialAndScienceAsOneInquiryDomain() {
        GradeVerificationResponse verification = verification(
            SelectionStrategy.ALL_COURSES,
            calculation(SubjectCategory.SOCIAL, true, "192", "2"),
            calculation(SubjectCategory.SCIENCE, true, "282", "3")
        );

        SyuScenarioExportSummary summary = SyuScenarioExportSummary.from(verification);

        assertThat(summary.subjectDomains()).singleElement().satisfies(domain -> {
            assertThat(domain.domainName()).isEqualTo("탐구");
            assertThat(domain.score()).isEqualByComparingTo("94.8");
        });
    }

    private GradeVerificationResponse verification(
        SelectionStrategy selectionStrategy,
        CourseCalculation... calculations
    ) {
        CalculationSummary calculationSummary = mock(CalculationSummary.class);
        when(calculationSummary.convertedScoreTimesCreditsSum()).thenReturn(BigDecimal.TEN);
        when(calculationSummary.totalIncludedCredits()).thenReturn(BigDecimal.ONE);
        when(calculationSummary.intermediateScale()).thenReturn(4);
        when(calculationSummary.intermediateRounding()).thenReturn(RoundingMode.HALF_UP);

        GradeVerificationResponse verification = mock(GradeVerificationResponse.class);
        when(verification.calculationSummary()).thenReturn(calculationSummary);
        when(verification.calculations()).thenReturn(List.of(calculations));
        when(verification.selectionStrategy()).thenReturn(selectionStrategy);
        when(verification.baseScore()).thenReturn(BigDecimal.TEN);
        return verification;
    }

    private CourseCalculation calculation(
        SubjectCategory category,
        boolean included,
        String weightedScore,
        String appliedWeight
    ) {
        CourseCalculation calculation = mock(CourseCalculation.class);
        when(calculation.included()).thenReturn(included);
        when(calculation.appliedSubjectCategory()).thenReturn(category);
        if (included) {
            when(calculation.weightedScore()).thenReturn(new BigDecimal(weightedScore));
            when(calculation.appliedWeight()).thenReturn(new BigDecimal(appliedWeight));
        }
        return calculation;
    }
}
