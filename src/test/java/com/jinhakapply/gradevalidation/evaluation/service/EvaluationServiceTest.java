package com.jinhakapply.gradevalidation.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementConversion;
import com.jinhakapply.gradevalidation.evaluation.domain.AchievementLevel;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.domain.ScoreAggregation;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.dto.EvaluationRuleActionRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.VerifyGradeRequest;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.global.code.ApiResponseCode;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {
    @Mock EvaluationRuleRepository ruleRepository;
    @Mock UniversityRepository universityRepository;
    @InjectMocks EvaluationService service;

    @Test
    void appliesGradeSubjectAndCreditWeights() {
        mockRule(rule(SelectionStrategy.ALL_COURSES, 0, ScoreAggregation.COURSE_SCORE_AVERAGE,
            decimals("20", "30", "50"), decimals("1", "2", "1", "0", "2", "0")));
        VerifyGradeRequest request = new VerifyGradeRequest(1L, List.of(
            course(1, 1, SubjectCategory.KOREAN, "국어", 1, "4"),
            course(3, 1, SubjectCategory.MATH, "미적분", 3, "4"),
            course(2, 1, SubjectCategory.SOCIAL, "사회", 1, "3")));

        GradeVerificationResponse response = service.verify(request);

        assertThat(response.finalScore()).isEqualByComparingTo("91.6667");
        assertThat(response.includedCourseCount()).isEqualTo(2);
        assertThat(response.excludedCourseCount()).isEqualTo(1);
    }

    @Test
    void selectsTheTwoBestSemestersBeforeCalculating() {
        mockRule(rule(SelectionStrategy.TOP_N_SEMESTERS, 2, ScoreAggregation.COURSE_SCORE_AVERAGE,
            decimals("33.3333", "33.3333", "33.3334"), decimals("1", "1", "1", "1", "1", "1")));
        VerifyGradeRequest request = new VerifyGradeRequest(1L, List.of(
            course(1, 1, SubjectCategory.KOREAN, "국어1", 5, "3"),
            course(1, 2, SubjectCategory.KOREAN, "국어2", 1, "3"),
            course(2, 1, SubjectCategory.MATH, "수학1", 2, "3"),
            course(2, 2, SubjectCategory.MATH, "수학2", 7, "3")));

        GradeVerificationResponse response = service.verify(request);

        assertThat(response.includedCourseCount()).isEqualTo(2);
        assertThat(response.calculations()).filteredOn(GradeVerificationResponse.CourseCalculation::included)
            .extracting(GradeVerificationResponse.CourseCalculation::courseName)
            .containsExactlyInAnyOrder("국어2", "수학1");
    }

    @Test
    void convertsAchievementWithZScoreWhenConfigured() {
        EvaluationRule zScoreRule = rule(SelectionStrategy.ALL_COURSES, 0, ScoreAggregation.COURSE_SCORE_AVERAGE,
            decimals("33.3333", "33.3333", "33.3334"), decimals("1", "1", "1", "1", "1", "1"),
            AchievementConversion.Z_SCORE);
        mockRule(zScoreRule);
        VerifyGradeRequest.CourseGrade course = new VerifyGradeRequest.CourseGrade(1, 1, SubjectCategory.SCIENCE,
            "과학탐구실험", null, AchievementLevel.A, new BigDecimal("97"), new BigDecimal("82.4"),
            new BigDecimal("6.9"), 48, true, false, new BigDecimal("3"));

        GradeVerificationResponse response = service.verify(new VerifyGradeRequest(1L, List.of(course)));

        assertThat(response.calculations().get(0).effectiveGrade()).isEqualByComparingTo("1");
        assertThat(response.finalScore()).isEqualByComparingTo("100.0000");
    }

    @Test
    void choosesScienceOrSocialByTotalCreditsForEngineeringUniversityRule() {
        mockRule(rule(SelectionStrategy.CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N, 4,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("33.3333", "33.3333", "33.3334"),
            decimals("1", "1", "1", "1", "1", "0")));
        VerifyGradeRequest request = new VerifyGradeRequest(1L, List.of(
            course(1, 1, SubjectCategory.KOREAN, "국어", 2, "3"),
            course(1, 1, SubjectCategory.MATH, "수학", 2, "3"),
            course(1, 1, SubjectCategory.ENGLISH, "영어", 2, "3"),
            course(1, 1, SubjectCategory.SOCIAL, "사회", 1, "4"),
            course(1, 1, SubjectCategory.SCIENCE, "과학", 5, "5")));

        GradeVerificationResponse response = service.verify(request);

        assertThat(response.calculations()).filteredOn(GradeVerificationResponse.CourseCalculation::included)
            .extracting(GradeVerificationResponse.CourseCalculation::courseName).contains("과학").doesNotContain("사회");
    }

    @Test
    void rejectsDraftRuleForGradeCalculation() {
        EvaluationRule draft = rule(SelectionStrategy.ALL_COURSES, 0, ScoreAggregation.COURSE_SCORE_AVERAGE,
            decimals("33.3333", "33.3333", "33.3334"), decimals("1", "1", "1", "1", "1", "1"));
        ReflectionTestUtils.setField(draft, "status", EvaluationRuleStatus.DRAFT);
        ReflectionTestUtils.setField(draft, "active", false);
        mockRule(draft);

        assertThatThrownBy(() -> service.verify(new VerifyGradeRequest(1L, List.of(
            course(1, 1, SubjectCategory.KOREAN, "국어", 1, "3")
        )))).isInstanceOfSatisfying(CustomException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INVALID_EVALUATION_RULE_STATUS));
    }

    @Test
    void publishingRuleRetiresPreviousPublishedVersion() {
        EvaluationRule candidate = rule(SelectionStrategy.ALL_COURSES, 0, ScoreAggregation.COURSE_SCORE_AVERAGE,
            decimals("33.3333", "33.3333", "33.3334"), decimals("1", "1", "1", "1", "1", "1"));
        ReflectionTestUtils.setField(candidate, "status", EvaluationRuleStatus.VERIFIED);
        ReflectionTestUtils.setField(candidate, "active", false);
        EvaluationRule previous = rule(SelectionStrategy.ALL_COURSES, 0, ScoreAggregation.COURSE_SCORE_AVERAGE,
            decimals("33.3333", "33.3333", "33.3334"), decimals("1", "1", "1", "1", "1", "1"));
        when(ruleRepository.findOneById(1L)).thenReturn(Optional.of(candidate));
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndAdmissionTypeAndRecruitmentUnitAndStatus(
            candidate.getUniversity().getId(), 2027, candidate.getAdmissionType(), candidate.getRecruitmentUnit(),
            EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of(previous));

        service.publishRule(1L, new EvaluationRuleActionRequest("reviewer", "게시 승인"));

        assertThat(candidate.getStatus()).isEqualTo(EvaluationRuleStatus.PUBLISHED);
        assertThat(candidate.isActive()).isTrue();
        assertThat(previous.getStatus()).isEqualTo(EvaluationRuleStatus.RETIRED);
        assertThat(previous.isActive()).isFalse();
    }

    private void mockRule(EvaluationRule rule) {
        when(ruleRepository.findOneById(1L)).thenReturn(Optional.of(rule));
    }

    private EvaluationRule rule(SelectionStrategy selection, int selectionCount, ScoreAggregation aggregation,
        List<BigDecimal> gradeWeights, List<BigDecimal> subjectWeights) {
        return rule(selection, selectionCount, aggregation, gradeWeights, subjectWeights, AchievementConversion.DIRECT_TABLE);
    }

    private EvaluationRule rule(SelectionStrategy selection, int selectionCount, ScoreAggregation aggregation,
        List<BigDecimal> gradeWeights, List<BigDecimal> subjectWeights, AchievementConversion achievementConversion) {
        University university = University.create("TEST", "테스트대학교");
        EvaluationRule rule = EvaluationRule.create(university, "테스트 규칙", 2027, "학생부교과", "전체", 1,
            gradeWeights, subjectWeights, decimals("100", "95", "90", "85", "80", "70", "60", "50", "40"),
            selection, selectionCount, 2, aggregation, achievementConversion, false, false, false, 4, RoundingMode.HALF_UP,
            4, RoundingMode.HALF_UP, BigDecimal.ONE, decimals("1", "3", "5"), decimals("100", "95", "90"),
            List.of(1, 2, 3, 4, 5, 6), "테스트 모집요강", "1-2", null, null);
        rule.markVerified("tester", null);
        rule.publish("tester", null);
        return rule;
    }

    private static VerifyGradeRequest.CourseGrade course(int year, int semester, SubjectCategory category,
        String name, int grade, String credits) {
        return new VerifyGradeRequest.CourseGrade(year, semester, category, name, grade, null,
            null, null, null, null, false, false, new BigDecimal(credits));
    }

    private static List<BigDecimal> decimals(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }
}
