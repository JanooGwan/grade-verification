package com.jinhakapply.gradevalidation.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.university.domain.University;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        일반[실기위주]|실용음악과|정원내 일반전형(실기위주)|실기학과
        일반[면접위주]|항공서비스과|정원내 일반전형(면접위주)|항공서비스과
        특별[일반고]|컴퓨터공학과|정원내 특별전형(일반고)|전체 모집단위
        특별[특성화고]|컴퓨터공학과|정원내 특별전형(특성화고)|전체 모집단위
        특별[연계교육]|컴퓨터공학과|정원내 특별전형(협약을통한연계교육)|전체 모집단위
        특별[대학자체기준(어학우수자)]|항공서비스과|정원내 특별전형(대학자체기준)|항공서비스과
        농어촌|컴퓨터공학과|정원외 특별전형(농어촌학생)|일반학과
        기회균형|컴퓨터공학과|정원외 특별전형(기회균형)|일반학과
        """)
    void connectsCurrentSourceTrackNamesToPublishedAcademicRules(
        String sourceTrack, String unit, String publishedTrack, String publishedUnit) {
        EvaluationRule rule = rule(publishedTrack, publishedUnit);
        assertThat(matcher.matchesAdmissionType(rule, sourceTrack, unit)).isTrue();
        assertThat(matcher.matchesRecruitmentUnit(rule, unit)).isTrue();
    }

    @Test
    void keepsSeparatePracticalAndInterviewTracksFromCrossMatching() {
        EvaluationRule practical = rule("정원내 일반전형(실기위주)", "실기학과");
        EvaluationRule interview = rule("정원내 일반전형(면접위주)", "항공서비스과");
        assertThat(matcher.matchesAdmissionType(practical, "일반[면접위주]", "항공서비스과")).isFalse();
        assertThat(matcher.matchesAdmissionType(interview, "일반[실기위주]", "실용음악과")).isFalse();
        assertThat(matcher.matchesAdmissionType(practical, "일반[실기위주]", "컴퓨터공학과")).isFalse();
        assertThat(matcher.matchesAdmissionType(interview, "일반[면접위주]", "컴퓨터공학과")).isFalse();
        when(practical.getUniversity().getCode()).thenReturn("OTHER");
        assertThat(matcher.matchesAdmissionType(practical, "일반[실기위주]", "실용음악과")).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        정원외 특별전형(농어촌학생)|문예창작과
        정원외 특별전형(농어촌학생)|산업디자인학과
        정원외 특별전형(농어촌학생)|사회체육학과
        정원외 특별전형(기회균형)|문예창작과
        정원외 특별전형(기회균형)|산업디자인학과
        정원외 특별전형(기회균형)|사회체육학과
        """)
    void doesNotApplyGeneralPracticalDepartmentWeightToRuralAndOpportunityTracks(String track, String unit) {
        assertThat(matcher.matchesRecruitmentUnit(rule(track, "일반학과"), unit)).isTrue();
        assertThat(matcher.matchesRecruitmentUnit(rule(track, "실기학과"), unit)).isFalse();
        assertThat(matcher.matchesRecruitmentUnit(rule("정원내 일반전형(실기위주)", "실기학과"), unit)).isTrue();
        for (String practicalUnit : new String[]{"실용음악과", "연극영상학과"}) {
            assertThat(matcher.matchesRecruitmentUnit(rule(track, "실기학과"), practicalUnit)).isTrue();
            assertThat(matcher.matchesRecruitmentUnit(rule(track, "일반학과"), practicalUnit)).isFalse();
        }
        assertThat(matcher.matchesRecruitmentUnit(rule(track, "일반학과"), "항공서비스과")).isFalse();
        assertThat(matcher.matchesRecruitmentUnit(rule(track, "실기학과"), "항공서비스과")).isFalse();
    }

    @Test
    void mapsKbu2027RenamedHealthDepartmentAndRegularAviationToCorrectScoreGroups() {
        EvaluationRule health = rule("수시 일반", "간호·치위생·작업치료·임상병리·물리치료");
        when(health.getUniversity().getCode()).thenReturn("KBOK");
        when(health.getAdmissionYear()).thenReturn(2027);
        assertThat(matcher.matchesRecruitmentUnit(health, "물리치료학과")).isTrue();
        EvaluationRule regular = rule("정시 일반(학생부)", "일반학과");
        when(regular.getUniversity().getCode()).thenReturn("KBOK");
        when(regular.getAdmissionYear()).thenReturn(2027);
        assertThat(matcher.matchesRecruitmentUnit(regular, "항공서비스과")).isTrue();
        assertThat(matcher.matchesRecruitmentUnit(regular, "준오헤어디자인과")).isTrue();
        assertThat(matcher.matchesRecruitmentUnit(regular, "물리치료학과")).isFalse();
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
