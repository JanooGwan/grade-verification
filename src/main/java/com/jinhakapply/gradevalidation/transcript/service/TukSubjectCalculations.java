package com.jinhakapply.gradevalidation.transcript.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse.CourseCalculation;
import com.jinhakapply.gradevalidation.transcript.service.TranscriptBatchVerificationResult.IntermediateCalculation;

/** 저장된 과목별 계산 내역을 요약한다. 과목 재선택이나 최종 점수 재계산은 하지 않는다. */
final class TukSubjectCalculations {
    static final String SUBJECT = "반영교과";
    static final String INQUIRY = "탐구선택 비교";
    static final List<SubjectCategory> SUBJECTS = List.of(SubjectCategory.KOREAN, SubjectCategory.ENGLISH,
        SubjectCategory.MATH, SubjectCategory.SOCIAL, SubjectCategory.SCIENCE, SubjectCategory.OTHER);
    private static final Map<SubjectCategory, String> LABELS = Map.of(SubjectCategory.KOREAN, "국어",
        SubjectCategory.ENGLISH, "영어", SubjectCategory.MATH, "수학", SubjectCategory.SOCIAL, "사회",
        SubjectCategory.SCIENCE, "과학", SubjectCategory.OTHER, "기타");

    private TukSubjectCalculations() {}

    static String label(SubjectCategory category) { return LABELS.get(category); }

    static List<IntermediateCalculation> summarize(EvaluationRule rule, GradeVerificationResponse result) {
        if (result.calculations() == null || result.calculations().isEmpty()) return List.of();
        List<IntermediateCalculation> summaries = new ArrayList<>();
        for (SubjectCategory subject : SUBJECTS) {
            List<CourseCalculation> selected = result.calculations().stream().filter(CourseCalculation::included)
                .filter(course -> course.appliedSubjectCategory() == subject).toList();
            BigDecimal credits = BigDecimal.ZERO, gradeSum = BigDecimal.ZERO, scoreSum = BigDecimal.ZERO;
            for (CourseCalculation course : selected) {
                credits = credits.add(course.appliedCredits());
                gradeSum = gradeSum.add(course.effectiveGrade().multiply(course.appliedCredits()));
                scoreSum = scoreSum.add(course.convertedScore().multiply(course.appliedCredits()));
            }
            // 교과별 평균은 설명용이며 전체 M은 각 교과 평균의 단순평균으로 만들지 않는다.
            summaries.add(new IntermediateCalculation(SUBJECT, label(subject), !selected.isEmpty(), null,
                selected.size(), credits, gradeSum, average(gradeSum, credits), scoreSum, average(scoreSum, credits)));
        }
        if (result.selectionStrategy() == SelectionStrategy.CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N) {
            List<SubjectCategory> inquirySubjects = List.of(SubjectCategory.SOCIAL, SubjectCategory.SCIENCE);
            Map<SubjectCategory, List<CourseCalculation>> eligible = new java.util.EnumMap<>(SubjectCategory.class);
            Map<SubjectCategory, BigDecimal> credits = new java.util.EnumMap<>(SubjectCategory.class);
            for (SubjectCategory subject : inquirySubjects) {
                // EvaluationService의 선택 전 후보와 동일: 미반영 학기·평가정보 없는 과목 제외, 한국사 제외.
                List<CourseCalculation> candidates = result.calculations().stream()
                    .filter(course -> course.subjectCategory() == subject)
                    .filter(course -> course.effectiveGrade() != null && course.convertedScore() != null)
                    .filter(course -> !course.courseName().replaceAll("\\s", "").contains("한국사")).toList();
                eligible.put(subject, candidates);
                credits.put(subject, candidates.stream().map(CourseCalculation::credits).reduce(BigDecimal.ZERO, BigDecimal::add));
            }
            // 반영된 탐구 과목이 있으면 저장된 실제 선택을 우선 사용한다.
            SubjectCategory selected = result.calculations().stream().filter(CourseCalculation::included)
                .map(CourseCalculation::appliedSubjectCategory).filter(inquirySubjects::contains).findFirst()
                .orElseGet(() -> inquirySubjects.stream().max(Comparator
                    .comparing((SubjectCategory subject) -> credits.get(subject))
                    .thenComparing(subject -> -rule.getSubjectPriorities().getOrDefault(subject, Integer.MAX_VALUE)))
                    .orElse(SubjectCategory.SOCIAL));
            for (SubjectCategory subject : inquirySubjects) {
                summaries.add(new IntermediateCalculation(INQUIRY, label(subject), selected == subject, null,
                    eligible.get(subject).size(), credits.get(subject), null, null, null, null));
            }
        }
        return List.copyOf(summaries);
    }

    private static BigDecimal average(BigDecimal sum, BigDecimal credits) {
        return credits.signum() == 0 ? null : sum.divide(credits, 4, RoundingMode.HALF_UP);
    }
}
