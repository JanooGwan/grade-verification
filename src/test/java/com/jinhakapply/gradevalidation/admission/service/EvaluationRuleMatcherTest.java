package com.jinhakapply.gradevalidation.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.university.domain.University;
import org.junit.jupiter.api.Test;

class EvaluationRuleMatcherTest {

    private final EvaluationRuleMatcher matcher = new EvaluationRuleMatcher();

    @Test
    void mapsMjcSourceTrackAndPracticalDepartmentToPublishedRule() {
        EvaluationRule rule = rule("정원내 일반전형(실기위주)", "실기학과");

        assertThat(matcher.matchesAdmissionType(rule, "일반[실기·면접위주]", "실용음악과")).isTrue();
        assertThat(matcher.matchesRecruitmentUnit(rule, "실용음악과")).isTrue();
        assertThat(matcher.matchesAdmissionType(rule, "일반[실기·면접위주]", "항공서비스과")).isFalse();
    }

    @Test
    void mapsMjcSourceTrackAndAviationDepartmentToInterviewRule() {
        EvaluationRule rule = rule("정원내 일반전형(면접위주)", "항공서비스과");

        assertThat(matcher.matchesAdmissionType(rule, "일반[실기·면접위주]", "항공서비스과")).isTrue();
        assertThat(matcher.matchesRecruitmentUnit(rule, "항공서비스과")).isTrue();
    }

    @Test
    void mapsEveryMjcSourceTrackAlias() {
        assertThat(matcher.matchesAdmissionType(rule("정원내 특별전형(일반고)", "전체 모집단위"),
            "특별[일반고]", "컴퓨터공학과")).isTrue();
        assertThat(matcher.matchesAdmissionType(rule("정원내 특별전형(특성화고)", "전체 모집단위"),
            "특별[특성화고]", "컴퓨터공학과")).isTrue();
        assertThat(matcher.matchesAdmissionType(rule("정원내 특별전형(협약을통한연계교육)", "전체 모집단위"),
            "특별[연계교육]", "컴퓨터공학과")).isTrue();
        assertThat(matcher.matchesAdmissionType(rule("정원내 특별전형(대학자체기준)", "항공서비스과"),
            "특별[대학자체기준]", "항공서비스과")).isTrue();
        assertThat(matcher.matchesAdmissionType(rule("정원외 특별전형(농어촌학생)", "일반학과"),
            "농어촌", "컴퓨터공학과")).isTrue();
        assertThat(matcher.matchesAdmissionType(rule("정원외 특별전형(기회균형)", "일반학과"),
            "기회균형", "컴퓨터공학과")).isTrue();
    }

    private EvaluationRule rule(String admissionType, String recruitmentUnit) {
        University university = mock(University.class);
        when(university.getCode()).thenReturn("MJC");
        EvaluationRule rule = mock(EvaluationRule.class);
        when(rule.getUniversity()).thenReturn(university);
        when(rule.getAdmissionType()).thenReturn(admissionType);
        when(rule.getRecruitmentUnit()).thenReturn(recruitmentUnit);
        return rule;
    }
}
