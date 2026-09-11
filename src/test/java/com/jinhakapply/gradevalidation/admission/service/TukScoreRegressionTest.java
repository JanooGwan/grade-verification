package com.jinhakapply.gradevalidation.admission.service;

import static org.assertj.core.api.Assertions.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import com.jinhakapply.gradevalidation.admission.domain.*;
import com.jinhakapply.gradevalidation.admission.dto.CalculateApplicationScoreRequest;
import com.jinhakapply.gradevalidation.evaluation.domain.*;
import com.jinhakapply.gradevalidation.evaluation.dto.*;
import com.jinhakapply.gradevalidation.evaluation.policy.DeclarativeSelectionPolicyEngine;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.*;
import com.jinhakapply.gradevalidation.university.domain.University;
import org.junit.jupiter.api.Test;

class TukScoreRegressionTest {
    private final EvaluationService evaluator = new EvaluationService(null, null, new DeclarativeSelectionPolicyEngine());
    private final GuidebookQuantitativeScoreCalculator calculator = new GuidebookQuantitativeScoreCalculator();

    public static EvaluationRule rule(int year, String track, String unit) {
        boolean business = unit.equals("경영학부"), all = unit.equals("전체 모집단위");
        EvaluationRule rule = EvaluationRule.create(University.create("TUK", "한국공학대학교"), "합성 검증 규칙", year,
            track, unit, 1, numbers("1,1,1"), numbers(all ? "1,1,1,1,1,1" : business ? "1,1,1,1,1,0" : "1,1,1,0,1,0"),
            numbers("100,99,98,97,96,94,80,60,25"), all ? SelectionStrategy.ALL_COURSES : business
                ? SelectionStrategy.CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N : SelectionStrategy.CORE_SCIENCE_TOP_N,
            all ? 0 : 4, all ? 0 : 2, 0, ScoreAggregation.COURSE_SCORE_AVERAGE,
            all ? AchievementConversion.EXCLUDE : AchievementConversion.DIRECT_TABLE, false, true, true, false, false,
            4, RoundingMode.HALF_UP, 4, RoundingMode.HALF_UP, new BigDecimal(track.contains("논술") ? "1" : "5"),
            numbers("1,2,4"), numbers("100,99,97"), List.of(3,4,5,1,2,6), "합성", "1", null, null);
        rule.markVerified("test", null); rule.publish("test", null); return rule;
    }
    private static List<BigDecimal> numbers(String values) { return Arrays.stream(values.split(",")).map(BigDecimal::new).toList(); }
    private VerifyGradeRequest.CourseGrade course(String name, Integer grade, AchievementLevel achievement, boolean career, String credits) {
        return new VerifyGradeRequest.CourseGrade(1,1,SubjectCategory.SCIENCE,name,grade,achievement,null,null,null,100,career,false,new BigDecimal(credits));
    }
    @Test void excludesOrdinaryAchievementButKeepsCareerConversionAndOneCredit() {
        var rule = rule(2026,"학생부교과(교과우수자)","공학계열");
        var result = evaluator.verify(rule,new VerifyGradeRequest(1L,List.of(
            course("물리학",3,null,false,"3"), course("과학탐구실험",null,AchievementLevel.A,false,"2"),
            course("진로과학",null,AchievementLevel.C,true,"5"))));
        assertThat(result.calculations().get(1).included()).isFalse();
        assertThat(result.calculations().get(2).effectiveGrade()).isEqualByComparingTo("4");
        assertThat(result.calculations().get(2).appliedCredits()).isEqualByComparingTo("1");
        assertThat(result.finalScore()).isEqualByComparingTo("488.7500");
    }
    @Test void rejectsCareerOnlyAndUnrankedOrdinaryOnlyApplicants() {
        var rule = rule(2026,"학생부교과(교과우수자)","공학계열");
        for (boolean career : List.of(false,true)) {
            assertThatThrownBy(() -> evaluator.verify(rule,new VerifyGradeRequest(1L,List.of(
                course("과학",null,AchievementLevel.A,career,"3")))))
                .isInstanceOfSatisfying(CustomException.class,
                    error -> assertThat(error.getDetail()).contains("석차등급"));
        }
    }
    @Test void excludesAchievementSubjectsFromSpecializedTrack() {
        var result=evaluator.verify(rule(2026,"학생부교과(특성화고교졸업자)","전체 모집단위"),new VerifyGradeRequest(1L,List.of(
            course("물리학",3,null,false,"3"),course("진로과학",null,AchievementLevel.A,true,"5"))));
        assertThat(result.finalScore()).isEqualByComparingTo("490");
        assertThat(result.includedCourseCount()).isEqualTo(1);
    }
    @Test void usesGraduationMonthAtBothYearBoundariesAndKeepsMissingMonthPending() {
        for(int year : List.of(2026,2027)) {
            var rule=rule(year,"논술(논술우수자)","공학계열");
            var transcript=evaluator.verify(rule,new VerifyGradeRequest(1L,List.of(course("과학",3,null,false,"3"))));
            for(int month : List.of(2,3,6,12)) {
                var common=common(year-2,LocalDate.of(year-2,month,1));
                var result=calculator.calculate(rule,rule.getAdmissionType(),transcript,new CalculateApplicationScoreRequest(new BigDecimal("300"),null),common);
                assertThat(result.finalScore()).isEqualByComparingTo(month==2 ? "360" : "398");
            }
            var pending=calculator.calculate(rule,rule.getAdmissionType(),null,
                new CalculateApplicationScoreRequest(new BigDecimal("300"),null),common(year-2,null));
            assertThat(pending.status()).isEqualTo(ApplicationScoreStatus.QUALITATIVE_PENDING);
            assertThat(pending.finalScore()).isNull();
            assertThat(pending.pendingComponents()).anyMatch(s->s.contains("졸업연월"));
        }
    }
    @Test void retainsFourDecimalsThroughEssayAdditionAndViolenceDeduction() {
        var rule=rule(2026,"논술(논술우수자)","공학계열");
        var transcript=evaluator.verify(rule,new VerifyGradeRequest(1L,List.of(
            course("과학1",3,null,false,"2"),course("과학2",4,null,false,"1"))));
        var common = new StudentCommonEvaluationSnapshot(EducationBackground.DOMESTIC_HIGH_SCHOOL,HighSchoolType.GENERAL,
            GraduationStatus.GRADUATE,2025,null,List.of(),List.of(),List.of(),
            List.of(new StudentCommonEvaluationSnapshot.SchoolViolenceAction(1,1,null,true,null)),LocalDate.of(2025,2,1));
        var result=calculator.calculate(rule,rule.getAdmissionType(),transcript,new CalculateApplicationScoreRequest(new BigDecimal("350.1234"),null),common);
        assertThat(result.academicScore()).isEqualByComparingTo("97.6667");
        assertThat(result.finalScore()).isEqualByComparingTo("437.7901");
    }
    @Test void matchesKnownMajorsAndPreviousYearAliasesWithoutGuessingUnknownNames() {
        var matcher=new EvaluationRuleMatcher();
        var engineering=rule(2026,"논술(논술우수자)","공학계열");
        var business=rule(2026,"논술(논술우수자)","경영학부");
        assertThat(matcher.matchesRecruitmentUnit(engineering,"기계공학과")).isTrue();
        assertThat(matcher.matchesRecruitmentUnit(engineering,"자유전공학부")).isTrue();
        assertThat(matcher.matchesRecruitmentUnit(business,"경영 자율전공")).isTrue();
        assertThat(matcher.matchesRecruitmentUnit(engineering,"경영학전공")).isFalse();
        assertThat(matcher.matchesRecruitmentUnit(engineering,"미등록학과")).isFalse();
        assertThat(matcher.matchesRecruitmentUnit(engineering,"AI융합 자율전공")).isFalse();
        assertThat(matcher.matchesRecruitmentUnit(rule(2027,"논술(논술우수자)","공학계열"),"AI융합 자율전공")).isTrue();
        var rule2027 = rule(2027,"논술(논술우수자)","공학계열");
        for (var names : Map.of("SW 자율전공", "AI융합 자율전공", "전력응용시스템전공", "전기공학전공",
            "미래에너지시스템전공", "에너지공학전공").entrySet()) {
            assertThat(TukRecruitmentUnits.nameForRuleYear(2027, names.getKey())).isEqualTo(names.getValue());
            assertThat(matcher.matchesRecruitmentUnit(rule2027, names.getKey())).isTrue();
            assertThat(matcher.matchesRecruitmentUnit(rule2027, names.getValue())).isTrue();
        }
    }
    private StudentCommonEvaluationSnapshot common(int year,LocalDate date) {
        return new StudentCommonEvaluationSnapshot(EducationBackground.DOMESTIC_HIGH_SCHOOL,HighSchoolType.GENERAL,
            GraduationStatus.GRADUATE,year,null,List.of(),List.of(),List.of(),List.of(),date);
    }
}
