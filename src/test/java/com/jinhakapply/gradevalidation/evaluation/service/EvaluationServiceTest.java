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
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.transcript.domain.LegacyAchievement;
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
    void convertsZeroStandardDeviationToNinthGradeDefensively() {
        EvaluationRule zScoreRule = rule(SelectionStrategy.ALL_COURSES, 0,
            ScoreAggregation.COURSE_SCORE_AVERAGE,
            decimals("33.3333", "33.3333", "33.3334"), decimals("1", "1", "1", "1", "1", "1"),
            AchievementConversion.Z_SCORE);
        mockRule(zScoreRule);
        VerifyGradeRequest.CourseGrade course = new VerifyGradeRequest.CourseGrade(
            1, 1, SubjectCategory.SCIENCE, "과학탐구실험", null, AchievementLevel.A,
            new BigDecimal("97"), new BigDecimal("82.4"), BigDecimal.ZERO, 48,
            true, false, new BigDecimal("3")
        );

        GradeVerificationResponse response = service.verify(new VerifyGradeRequest(1L, List.of(course)));

        assertThat(response.calculations().getFirst().effectiveGrade()).isEqualByComparingTo("9");
        assertThat(response.finalScore()).isEqualByComparingTo("40.0000");
    }

    @Test
    void convertsLegacyRankTieAndSuWooMiYangGaWithTrace() {
        EvaluationRule legacyRule = rule(SelectionStrategy.ALL_COURSES, 0,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("33.3333", "33.3333", "33.3334"),
            decimals("1", "1", "1", "1", "1", "1"));
        ReflectionTestUtils.setField(legacyRule.getUniversity(), "name", "한국공학대학교");
        ReflectionTestUtils.setField(legacyRule, "applyGradeWeights", false);
        mockRule(legacyRule);
        VerifyGradeRequest.CourseGrade ranked = new VerifyGradeRequest.CourseGrade(
            1, 1, SubjectCategory.KOREAN, "구교육과정 국어", null, GradeScale.LEGACY, null,
            null, null, null, 126, 30, 1, null, false, false, new BigDecimal("3")
        );
        VerifyGradeRequest.CourseGrade rated = new VerifyGradeRequest.CourseGrade(
            1, 1, SubjectCategory.MATH, "구교육과정 수학", null, GradeScale.LEGACY, null,
            null, null, null, null, null, null, LegacyAchievement.WOO, false, false,
            new BigDecimal("3")
        );

        GradeVerificationResponse response = service.verify(new VerifyGradeRequest(1L, List.of(ranked, rated)));

        assertThat(response.calculations().get(0).rankPercentile()).isEqualByComparingTo("23.81");
        assertThat(response.calculations().get(0).effectiveGrade()).isEqualByComparingTo("4");
        assertThat(response.calculations().get(1).effectiveGrade()).isEqualByComparingTo("3");
    }

    @Test
    void choosesScienceOrSocialByTotalCreditsForBusinessRule() {
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
    void choosesSocialWhenBusinessOptionalSubjectCreditsAreEqual() {
        mockRule(rule(SelectionStrategy.CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N, 4,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("33.3333", "33.3333", "33.3334"),
            decimals("1", "1", "1", "1", "1", "0")));
        VerifyGradeRequest request = new VerifyGradeRequest(1L, List.of(
            course(1, 1, SubjectCategory.KOREAN, "국어", 2, "3"),
            course(1, 1, SubjectCategory.MATH, "수학", 2, "3"),
            course(1, 1, SubjectCategory.ENGLISH, "영어", 2, "3"),
            course(1, 1, SubjectCategory.SOCIAL, "사회문화", 4, "4"),
            course(1, 1, SubjectCategory.SCIENCE, "물리학", 1, "4"),
            course(1, 1, SubjectCategory.SOCIAL, "한국사", 1, "3"),
            course(2, 1, SubjectCategory.SOCIAL, "한국사 심화", 2, "3")));

        GradeVerificationResponse response = service.verify(request);

        assertThat(response.calculations()).filteredOn(GradeVerificationResponse.CourseCalculation::included)
            .extracting(GradeVerificationResponse.CourseCalculation::courseName)
            .contains("사회문화", "한국사").doesNotContain("물리학", "한국사 심화");
        assertThat(response.calculations()).filteredOn(item -> item.courseName().equals("한국사"))
            .extracting(GradeVerificationResponse.CourseCalculation::appliedSubjectCategory)
            .containsExactly(SubjectCategory.SOCIAL);
    }

    @Test
    void engineeringRuleAlwaysUsesScienceAndTreatsOneKoreanHistoryCourseAsScience() {
        mockRule(rule(SelectionStrategy.CORE_SCIENCE_TOP_N, 4,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("33.3333", "33.3333", "33.3334"),
            decimals("1", "1", "1", "1", "1", "0")));
        VerifyGradeRequest request = new VerifyGradeRequest(1L, List.of(
            course(1, 1, SubjectCategory.KOREAN, "국어", 2, "3"),
            course(1, 1, SubjectCategory.MATH, "수학", 2, "3"),
            course(1, 1, SubjectCategory.ENGLISH, "영어", 2, "3"),
            course(1, 1, SubjectCategory.SOCIAL, "사회문화", 1, "6"),
            course(1, 1, SubjectCategory.SCIENCE, "물리학", 4, "3"),
            course(1, 1, SubjectCategory.SOCIAL, "한국사", 1, "3"),
            course(2, 1, SubjectCategory.SOCIAL, "한국사 심화", 2, "3")));

        GradeVerificationResponse response = service.verify(request);

        assertThat(response.calculations()).filteredOn(GradeVerificationResponse.CourseCalculation::included)
            .extracting(GradeVerificationResponse.CourseCalculation::courseName)
            .contains("물리학", "한국사").doesNotContain("사회문화", "한국사 심화");
        assertThat(response.calculations()).filteredOn(item -> item.courseName().equals("한국사"))
            .extracting(GradeVerificationResponse.CourseCalculation::appliedSubjectCategory)
            .containsExactly(SubjectCategory.SCIENCE);
    }

    @Test
    void appliesFourRegularAndTwoCareerCoursesPerSubject() {
        mockRule(rule(SelectionStrategy.CORE_SCIENCE_TOP_N, 4,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("33.3333", "33.3333", "33.3334"),
            decimals("1", "1", "1", "1", "1", "0")));
        VerifyGradeRequest request = new VerifyGradeRequest(1L, List.of(
            course(1, 1, SubjectCategory.SCIENCE, "과학1", 1, "3"),
            course(1, 2, SubjectCategory.SCIENCE, "과학2", 2, "3"),
            course(2, 1, SubjectCategory.SCIENCE, "과학3", 3, "3"),
            course(2, 2, SubjectCategory.SCIENCE, "과학4", 4, "3"),
            course(3, 1, SubjectCategory.SCIENCE, "과학5", 5, "3"),
            careerCourse(1, 1, SubjectCategory.SCIENCE, "진로1", AchievementLevel.A, "3"),
            careerCourse(2, 1, SubjectCategory.SCIENCE, "진로2", AchievementLevel.B, "3"),
            careerCourse(3, 1, SubjectCategory.SCIENCE, "진로3", AchievementLevel.C, "3")));

        GradeVerificationResponse response = service.verify(request);

        assertThat(response.includedCourseCount()).isEqualTo(6);
        assertThat(response.calculations()).filteredOn(GradeVerificationResponse.CourseCalculation::included)
            .extracting(GradeVerificationResponse.CourseCalculation::courseName)
            .containsExactlyInAnyOrder("과학1", "과학2", "과학3", "과학4", "진로1", "진로2");
    }

    @Test
    void tukCareerCourseUsesOneAppliedCreditAndExposesItInTrace() {
        EvaluationRule tukRule = rule(SelectionStrategy.CORE_SCIENCE_TOP_N, 4,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("33.3333", "33.3333", "33.3334"),
            decimals("1", "1", "1", "1", "1", "0"));
        ReflectionTestUtils.setField(tukRule.getUniversity(), "name", "한국공학대학교");
        ReflectionTestUtils.setField(tukRule, "applyGradeWeights", false);
        mockRule(tukRule);

        GradeVerificationResponse response = service.verify(new VerifyGradeRequest(1L, List.of(
            course(1, 1, SubjectCategory.SCIENCE, "과학", 3, "3"),
            careerCourse(1, 1, SubjectCategory.SCIENCE, "과학 진로", AchievementLevel.A, "5")
        )));

        assertThat(response.calculations()).filteredOn(item -> item.courseName().equals("과학 진로"))
            .extracting(GradeVerificationResponse.CourseCalculation::credits,
                GradeVerificationResponse.CourseCalculation::appliedCredits)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(new BigDecimal("5"), BigDecimal.ONE));
        assertThat(response.calculationSummary().totalIncludedCredits()).isEqualByComparingTo("4");
    }

    @Test
    void syuTopSubjectsAreChosenByConvertedDomainScoreNotRawAverageGrade() {
        EvaluationRule syuRule = rule(SelectionStrategy.TOP_N_SUBJECTS, 1,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("33.3333", "33.3333", "33.3334"),
            decimals("1", "1", "1", "1", "1", "0"));
        ReflectionTestUtils.setField(syuRule.getUniversity(), "name", "삼육대학교");
        ReflectionTestUtils.setField(syuRule, "applyGradeWeights", false);
        syuRule.getGradeScores().clear();
        List<BigDecimal> scores = decimals("100", "100", "99", "99", "98", "90", "90", "70", "70");
        for (int grade = 1; grade <= 9; grade++) syuRule.getGradeScores().put(grade, scores.get(grade - 1));
        mockRule(syuRule);

        GradeVerificationResponse response = service.verify(new VerifyGradeRequest(1L, List.of(
            course(1, 1, SubjectCategory.KOREAN, "국어1", 1, "1"),
            course(1, 2, SubjectCategory.KOREAN, "국어2", 8, "1"),
            course(2, 1, SubjectCategory.KOREAN, "국어3", 8, "1"),
            course(1, 1, SubjectCategory.MATH, "수학1", 5, "1"),
            course(1, 2, SubjectCategory.MATH, "수학2", 5, "1"),
            course(2, 1, SubjectCategory.MATH, "수학3", 5, "1")
        )));

        assertThat(response.calculations()).filteredOn(GradeVerificationResponse.CourseCalculation::included)
            .extracting(GradeVerificationResponse.CourseCalculation::subjectCategory)
            .containsOnly(SubjectCategory.MATH);
    }

    @Test
    void syuTreatsSocialAndScienceAsOneInquiryDomainWhenSelectingTopTwo() {
        EvaluationRule syuRule = rule(SelectionStrategy.TOP_N_SUBJECTS, 2,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("1", "1", "1"),
            decimals("1", "1", "1", "1", "1", "0"));
        ReflectionTestUtils.setField(syuRule.getUniversity(), "name", "삼육대학교");
        ReflectionTestUtils.setField(syuRule, "applyGradeWeights", false);
        syuRule.getGradeScores().clear();
        List<BigDecimal> scores = decimals("100", "100", "99", "99", "98", "90", "90", "70", "70");
        for (int grade = 1; grade <= 9; grade++) syuRule.getGradeScores().put(grade, scores.get(grade - 1));
        mockRule(syuRule);

        GradeVerificationResponse response = service.verify(new VerifyGradeRequest(1L, List.of(
            course(1, 1, SubjectCategory.KOREAN, "국어", 3, "1"),
            course(1, 1, SubjectCategory.SOCIAL, "사회", 1, "1"),
            course(1, 2, SubjectCategory.SCIENCE, "과학", 1, "1"),
            course(1, 2, SubjectCategory.MATH, "수학", 5, "1"),
            course(2, 1, SubjectCategory.ENGLISH, "영어", 6, "1")
        )));

        assertThat(response.calculations()).filteredOn(GradeVerificationResponse.CourseCalculation::included)
            .extracting(GradeVerificationResponse.CourseCalculation::courseName)
            .containsExactlyInAnyOrder("국어", "사회", "과학");
    }

    @Test
    void syuExcludesCoursesWithOnlyOneEnrolledStudent() {
        EvaluationRule syuRule = rule(SelectionStrategy.ALL_COURSES, 0,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("1", "1", "1"),
            decimals("1", "1", "1", "1", "1", "0"));
        ReflectionTestUtils.setField(syuRule.getUniversity(), "name", "삼육대학교");
        ReflectionTestUtils.setField(syuRule, "applyGradeWeights", false);
        mockRule(syuRule);
        VerifyGradeRequest.CourseGrade singleStudentCourse = new VerifyGradeRequest.CourseGrade(
            1, 1, SubjectCategory.KOREAN, "소인수 과목", 1, null,
            null, null, null, 1, false, false, new BigDecimal("3")
        );

        GradeVerificationResponse response = service.verify(new VerifyGradeRequest(1L, List.of(
            singleStudentCourse,
            course(1, 1, SubjectCategory.KOREAN, "국어1", 3, "3"),
            course(1, 2, SubjectCategory.KOREAN, "국어2", 3, "3"),
            course(2, 1, SubjectCategory.KOREAN, "국어3", 3, "3")
        )));

        assertThat(response.calculations()).filteredOn(item -> item.courseName().equals("소인수 과목"))
            .singleElement().satisfies(item -> {
                assertThat(item.included()).isFalse();
                assertThat(item.exclusionReason()).contains("재적인원이 1명");
            });
    }

    @Test
    void syuRequiresGradesFromAtLeastThreeSemesters() {
        EvaluationRule syuRule = rule(SelectionStrategy.ALL_COURSES, 0,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("1", "1", "1"),
            decimals("1", "1", "1", "1", "1", "0"));
        ReflectionTestUtils.setField(syuRule.getUniversity(), "name", "삼육대학교");
        ReflectionTestUtils.setField(syuRule, "applyGradeWeights", false);
        mockRule(syuRule);

        assertThatThrownBy(() -> service.verify(new VerifyGradeRequest(1L, List.of(
            course(1, 1, SubjectCategory.KOREAN, "국어1", 3, "3"),
            course(1, 2, SubjectCategory.KOREAN, "국어2", 3, "3")
        )))).isInstanceOfSatisfying(CustomException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.INSUFFICIENT_ELIGIBLE_COURSES);
            assertThat(exception.getDetail()).contains("3개 학기");
        });
    }

    @Test
    void syuTruncatesTheFifthDecimalPlaceAfterApplyingScoreMultiplier() {
        EvaluationRule syuRule = rule(SelectionStrategy.ALL_COURSES, 0,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("1", "1", "1"),
            decimals("1", "1", "1", "1", "1", "0"));
        ReflectionTestUtils.setField(syuRule.getUniversity(), "name", "삼육대학교");
        ReflectionTestUtils.setField(syuRule, "applyGradeWeights", false);
        ReflectionTestUtils.setField(syuRule, "intermediateScale", 10);
        ReflectionTestUtils.setField(syuRule, "finalScale", 4);
        ReflectionTestUtils.setField(syuRule, "finalRounding", RoundingMode.DOWN);
        ReflectionTestUtils.setField(syuRule, "scoreMultiplier", new BigDecimal("10"));
        syuRule.getGradeScores().clear();
        List<BigDecimal> scores = decimals("100", "100", "99", "99", "98", "90", "90", "70", "70");
        for (int grade = 1; grade <= 9; grade++) syuRule.getGradeScores().put(grade, scores.get(grade - 1));
        mockRule(syuRule);

        GradeVerificationResponse response = service.verify(new VerifyGradeRequest(1L, List.of(
            course(1, 1, SubjectCategory.KOREAN, "국어1", 1, "1"),
            course(1, 2, SubjectCategory.KOREAN, "국어2", 2, "1"),
            course(2, 1, SubjectCategory.KOREAN, "국어3", 3, "1")
        )));

        assertThat(response.finalScore()).isEqualByComparingTo("996.6666");
        assertThat(response.calculationSummary().finalRounding()).isEqualTo(RoundingMode.DOWN);
    }

    @Test
    void includesThirdYearSecondSemesterOnlyForGraduatesWhenConfigured() {
        EvaluationRule graduateRule = rule(SelectionStrategy.ALL_COURSES, 0,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("33.3333", "33.3333", "33.3334"),
            decimals("1", "1", "1", "1", "1", "0"));
        ReflectionTestUtils.setField(graduateRule, "includeThirdYearSecondSemesterForGraduates", true);
        mockRule(graduateRule);
        List<VerifyGradeRequest.CourseGrade> courses = List.of(
            course(3, 1, SubjectCategory.KOREAN, "화법과작문", 3, "3"),
            course(3, 2, SubjectCategory.KOREAN, "독서", 1, "3"));

        GradeVerificationResponse expectedGraduate = service.verify(new VerifyGradeRequest(1L, true, courses));
        GradeVerificationResponse expectedGraduation = service.verify(new VerifyGradeRequest(1L, false, courses));

        assertThat(expectedGraduate.includedCourseCount()).isEqualTo(2);
        assertThat(expectedGraduation.includedCourseCount()).isEqualTo(1);
    }

    @Test
    void appliesMjcTwoYearHighSchoolThirtyThirtyFortySemesters() {
        EvaluationRule mjcRule = rule(SelectionStrategy.BEST_SEMESTER_PER_GRADE, 0,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("30", "30", "40"),
            decimals("1", "1", "1", "1", "1", "1"));
        ReflectionTestUtils.setField(mjcRule.getUniversity(), "name", "명지전문대학교");
        ReflectionTestUtils.setField(mjcRule, "normalizeGradeWeights", true);
        mockRule(mjcRule);

        GradeVerificationResponse response = service.verify(new VerifyGradeRequest(
            1L, false, HighSchoolType.TWO_YEAR, List.of(
                course(1, 1, SubjectCategory.KOREAN, "1-1 국어", 1, "3"),
                course(1, 2, SubjectCategory.KOREAN, "1-2 국어", 2, "3"),
                course(2, 1, SubjectCategory.KOREAN, "2-1 국어", 3, "3"),
                course(2, 2, SubjectCategory.KOREAN, "2-2 국어", 1, "3")
            )
        ));

        assertThat(response.selectionStrategy()).isEqualTo(SelectionStrategy.ALL_COURSES);
        assertThat(response.includedCourseCount()).isEqualTo(3);
        assertThat(response.finalScore()).isEqualByComparingTo("94.5000");
        assertThat(response.calculationSummary().yearWeightDenominators()).containsOnlyKeys(11, 12, 21);
    }

    @Test
    void kbuPre2002AnnualRecordsUseAllGraduationYearGrades() {
        EvaluationRule kbuRule = rule(SelectionStrategy.TOP_N_SEMESTERS, 2,
            ScoreAggregation.AVERAGE_GRADE_THEN_SCORE, decimals("33.3333", "33.3333", "33.3334"),
            decimals("1", "1", "1", "1", "1", "1"));
        ReflectionTestUtils.setField(kbuRule.getUniversity(), "name", "경복대학교");
        ReflectionTestUtils.setField(kbuRule, "admissionYear", 2026);
        ReflectionTestUtils.setField(kbuRule, "applyGradeWeights", false);
        mockRule(kbuRule);

        GradeVerificationResponse response = service.verify(new VerifyGradeRequest(
            1L, true, HighSchoolType.GENERAL, 1996, List.of(
                course(1, 1, SubjectCategory.OTHER, "1학년 석차 요약", 2, "34"),
                course(2, 1, SubjectCategory.OTHER, "2학년 석차 요약", 4, "34"),
                course(3, 1, SubjectCategory.OTHER, "3학년 석차 요약", 6, "34")
            )
        ));

        assertThat(response.selectionStrategy()).isEqualTo(SelectionStrategy.ALL_COURSES);
        assertThat(response.includedCourseCount()).isEqualTo(3);
        assertThat(response.averageGrade()).isEqualByComparingTo("4.0000");
    }

    @Test
    void rejectsApplicantsWithFewerThanTwelveGradableCoursesAfterUnscoredCoursesAreExcluded() {
        EvaluationRule minimumTwelveRule = rule(SelectionStrategy.TOP_N_COURSES, 12,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("33.3333", "33.3333", "33.3334"),
            decimals("1", "1", "1", "1", "1", "0"));
        ReflectionTestUtils.setField(minimumTwelveRule, "minimumCourseCount", 12);
        mockRule(minimumTwelveRule);
        List<VerifyGradeRequest.CourseGrade> courses = new java.util.ArrayList<>(
            java.util.stream.IntStream.rangeClosed(1, 11)
            .mapToObj(index -> course(1 + (index - 1) / 4, 1, SubjectCategory.KOREAN,
                "국어" + index, 3, "3"))
            .toList()
        );
        courses.add(new VerifyGradeRequest.CourseGrade(
            3, 1, SubjectCategory.OTHER, "진로와 직업", null, null,
            null, null, null, null, false, false, BigDecimal.ONE
        ));

        assertThatThrownBy(() -> service.verify(new VerifyGradeRequest(1L, false, courses)))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;
                assertThat(customException.getErrorCode().getCode())
                    .isEqualTo("INSUFFICIENT_ELIGIBLE_COURSES");
                assertThat(customException.getDetail()).contains("최소 12과목");
            });
    }

    @Test
    void reproducesHanshinPublishedCalculationExample() {
        EvaluationRule hanshinRule = rule(SelectionStrategy.TOP_N_COURSES, 12,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("33.3333", "33.3333", "33.3334"),
            decimals("1", "1", "1", "1", "1", "0"));
        hanshinRule.getGradeScores().clear();
        List<BigDecimal> convertedScores = decimals("100", "99", "98", "97", "96", "95", "94", "80", "50");
        for (int grade = 1; grade <= convertedScores.size(); grade++) {
            hanshinRule.getGradeScores().put(grade, convertedScores.get(grade - 1));
        }
        ReflectionTestUtils.setField(hanshinRule, "minimumCourseCount", 12);
        ReflectionTestUtils.setField(hanshinRule, "applyGradeWeights", false);
        ReflectionTestUtils.setField(hanshinRule, "intermediateScale", 3);
        ReflectionTestUtils.setField(hanshinRule, "finalScale", 2);
        ReflectionTestUtils.setField(hanshinRule, "scoreMultiplier", new BigDecimal("10"));
        mockRule(hanshinRule);
        int[] grades = {3, 2, 4, 3, 2, 3, 2, 2, 2, 3, 3, 3};
        int[] credits = {5, 3, 5, 5, 3, 3, 2, 3, 3, 2, 5, 3};
        List<VerifyGradeRequest.CourseGrade> courses = java.util.stream.IntStream.range(0, grades.length)
            .mapToObj(index -> course(1 + index / 4, 1, SubjectCategory.KOREAN,
                "반영과목" + (index + 1), grades[index], Integer.toString(credits[index])))
            .toList();

        GradeVerificationResponse response = service.verify(new VerifyGradeRequest(1L, false, courses));

        assertThat(response.finalScore()).isEqualByComparingTo("982.14");
        assertThat(response.calculationSummary().gradeTimesCreditsSum()).isEqualByComparingTo("117");
        assertThat(response.calculationSummary().convertedScoreTimesCreditsSum()).isEqualByComparingTo("4125");
        assertThat(response.calculationSummary().totalIncludedCredits()).isEqualByComparingTo("42");
        assertThat(response.calculationSummary().gradeTimesWeightSum()).isEqualByComparingTo("117");
        assertThat(response.calculationSummary().convertedScoreTimesWeightSum()).isEqualByComparingTo("4125");
        assertThat(response.calculationSummary().totalAppliedWeight()).isEqualByComparingTo("42");
        assertThat(response.calculationSummary().baseScore()).isEqualByComparingTo("98.214");
        assertThat(response.calculationSummary().scoreBeforeFinalRounding()).isEqualByComparingTo("982.140");
        assertThat(response.calculationSummary().formula()).contains("이수단위 × 교과가중치");
    }

    @Test
    void hanshinSpecializedHighSchoolUsesAllOrdinaryCoursesInsteadOfTopTwelve() {
        EvaluationRule hanshinRule = rule(SelectionStrategy.TOP_N_COURSES, 12,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("33.3333", "33.3333", "33.3334"),
            decimals("1", "1", "1", "1", "1", "0"));
        ReflectionTestUtils.setField(hanshinRule.getUniversity(), "name", "한신대학교");
        ReflectionTestUtils.setField(hanshinRule, "admissionType", "학생부교과(학생부우수자)");
        mockRule(hanshinRule);
        List<VerifyGradeRequest.CourseGrade> courses = new java.util.ArrayList<>(java.util.stream.IntStream.rangeClosed(1, 12)
            .mapToObj(index -> course(1 + (index - 1) / 5, 1, SubjectCategory.KOREAN,
                "보통교과" + index, 1 + index % 9, "3"))
            .toList());
        courses.add(course(3, 1, SubjectCategory.OTHER, "예술교과", 3, "2"));

        GradeVerificationResponse response = service.verify(new VerifyGradeRequest(
            1L, false, HighSchoolType.SPECIALIZED, courses
        ));

        assertThat(response.selectionStrategy()).isEqualTo(SelectionStrategy.ALL_COURSES);
        assertThat(response.includedCourseCount()).isEqualTo(13);
    }

    @Test
    void allSubjectScopeUsesOneForNormalizedYearDenominator() {
        EvaluationRule hanshinRule = rule(SelectionStrategy.TOP_N_COURSES, 12,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("33.3333", "33.3333", "33.3334"),
            decimals("1", "1", "1", "1", "1", "0"));
        ReflectionTestUtils.setField(hanshinRule.getUniversity(), "name", "한신대학교");
        ReflectionTestUtils.setField(hanshinRule, "normalizeGradeWeights", true);
        mockRule(hanshinRule);
        VerifyGradeRequest.CourseGrade other = course(1, 1, SubjectCategory.OTHER, "예술 교과", 2, "3");

        GradeVerificationResponse response = service.verify(new VerifyGradeRequest(
            1L, false, HighSchoolType.SPECIALIZED, List.of(other)
        ));

        assertThat(response.finalScore()).isEqualByComparingTo("95.0000");
        assertThat(response.calculationSummary().yearWeightDenominators())
            .containsEntry(1, new BigDecimal("3"));
    }

    @Test
    void hanshinSpecializedGraduateTrackIncludesProfessionalCourses() {
        EvaluationRule hanshinRule = rule(SelectionStrategy.TOP_N_COURSES, 12,
            ScoreAggregation.COURSE_SCORE_AVERAGE, decimals("33.3333", "33.3333", "33.3334"),
            decimals("1", "1", "1", "1", "1", "0"));
        ReflectionTestUtils.setField(hanshinRule.getUniversity(), "name", "한신대학교");
        ReflectionTestUtils.setField(hanshinRule, "admissionType", "특성화고교졸업자전형");
        mockRule(hanshinRule);
        VerifyGradeRequest.CourseGrade professional = new VerifyGradeRequest.CourseGrade(
            1, 1, SubjectCategory.KOREAN, "전문교과", 2, null,
            null, null, null, null, false, true, new BigDecimal("3")
        );

        GradeVerificationResponse response = service.verify(new VerifyGradeRequest(
            1L, false, HighSchoolType.SPECIALIZED, List.of(professional)
        ));

        assertThat(response.selectionStrategy()).isEqualTo(SelectionStrategy.ALL_COURSES);
        assertThat(response.includedCourseCount()).isEqualTo(1);
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
            selection, selectionCount, 2, 0, aggregation, achievementConversion, false, false, false, true, false,
            4, RoundingMode.HALF_UP,
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

    private static VerifyGradeRequest.CourseGrade careerCourse(int year, int semester, SubjectCategory category,
        String name, AchievementLevel achievement, String credits) {
        return new VerifyGradeRequest.CourseGrade(year, semester, category, name, null, achievement,
            null, null, null, null, true, false, new BigDecimal(credits));
    }

    private static List<BigDecimal> decimals(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }
}
