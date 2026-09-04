package com.jinhakapply.gradevalidation.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.jinhakapply.gradevalidation.admission.domain.AdmissionTrack;
import com.jinhakapply.gradevalidation.admission.domain.RecruitmentUnit;
import com.jinhakapply.gradevalidation.admission.domain.RuleMatchStatus;
import com.jinhakapply.gradevalidation.admission.domain.StudentApplication;
import com.jinhakapply.gradevalidation.admission.repository.AdmissionTrackRepository;
import com.jinhakapply.gradevalidation.admission.repository.RecruitmentUnitRepository;
import com.jinhakapply.gradevalidation.admission.repository.StudentApplicationRepository;
import com.jinhakapply.gradevalidation.admission.repository.VerificationRunRepository;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.dto.VerifyGradeRequest;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptCourseRepository;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdmissionServiceTest {
    @Mock AdmissionTrackRepository trackRepository;
    @Mock RecruitmentUnitRepository unitRepository;
    @Mock StudentApplicationRepository applicationRepository;
    @Mock UniversityRepository universityRepository;
    @Mock StudentRepository studentRepository;
    @Mock StudentTranscriptCourseRepository courseRepository;
    @Mock EvaluationRuleRepository ruleRepository;
    @Mock EvaluationService evaluationService;
    @Mock VerificationRunRepository verificationRunRepository;
    @Mock ObjectMapper objectMapper;
    @Mock VerificationResultExcelWriter verificationResultExcelWriter;
    @Mock StudentApplication application;
    @Mock Student student;
    @Mock RecruitmentUnit unit;
    @Mock AdmissionTrack track;
    @Mock University university;

    private AdmissionService service;

    @BeforeEach
    void setUp() {
        service = new AdmissionService(
            trackRepository, unitRepository, applicationRepository, universityRepository,
            studentRepository, courseRepository, ruleRepository, evaluationService,
            verificationRunRepository, objectMapper, new EvaluationRuleMatcher(), verificationResultExcelWriter
        );
        when(applicationRepository.findOneById(20L)).thenReturn(Optional.of(application));
        when(application.getStudent()).thenReturn(student);
        when(student.getId()).thenReturn(10L);
        when(application.getRecruitmentUnit()).thenReturn(unit);
        org.mockito.Mockito.lenient().when(unit.getName()).thenReturn("컴퓨터공학과");
        when(unit.getAdmissionTrack()).thenReturn(track);
        org.mockito.Mockito.lenient().when(track.getName()).thenReturn("학생부교과");
        when(track.getAdmissionYear()).thenReturn(2027);
        when(track.getUniversity()).thenReturn(university);
        when(university.getId()).thenReturn(1L);
    }

    @Test
    void exactRecruitmentUnitRuleTakesPriorityOverCommonRule() {
        EvaluationRule exact = rule(101L, "컴퓨터공학과");
        EvaluationRule common = rule(102L, "전체 모집단위");
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            1L, 2027, EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of(common, exact));

        var result = service.matchRule(10L, 20L);

        assertThat(result.status()).isEqualTo(RuleMatchStatus.MATCHED);
        assertThat(result.matchedRuleId()).isEqualTo(101L);
    }

    @Test
    void reportsConflictWhenMultipleExactRulesArePublished() {
        EvaluationRule first = rule(101L, "컴퓨터공학과");
        EvaluationRule second = rule(102L, "컴퓨터 공학과");
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            1L, 2027, EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of(first, second));

        var result = service.matchRule(10L, 20L);

        assertThat(result.status()).isEqualTo(RuleMatchStatus.CONFLICT);
        assertThat(result.candidates()).hasSize(2);
    }

    @Test
    void reportsNotFoundWhenPublishedRuleDoesNotMatch() {
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            1L, 2027, EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of());

        var result = service.matchRule(10L, 20L);

        assertThat(result.status()).isEqualTo(RuleMatchStatus.NOT_FOUND);
        assertThat(result.matchedRuleId()).isNull();
    }

    @Test
    void passesStoredHighSchoolTypeToGradeVerification() {
        EvaluationRule exact = rule(101L, "컴퓨터공학과");
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            1L, 2027, EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of(exact));
        when(student.getGraduationStatus()).thenReturn(GraduationStatus.GRADUATE);
        when(student.getHighSchoolType()).thenReturn(HighSchoolType.SPECIALIZED);
        when(courseRepository.findAllByStudent_IdOrderBySchoolYearAscSemesterAscCourseNameAsc(10L))
            .thenReturn(List.of());
        RuntimeException stopAfterRequest = new RuntimeException("stop after request");
        when(evaluationService.verify(any(VerifyGradeRequest.class))).thenThrow(stopAfterRequest);

        assertThatThrownBy(() -> service.verifyApplication(10L, 20L)).isSameAs(stopAfterRequest);

        ArgumentCaptor<VerifyGradeRequest> request = ArgumentCaptor.forClass(VerifyGradeRequest.class);
        org.mockito.Mockito.verify(evaluationService).verify(request.capture());
        assertThat(request.getValue().highSchoolType()).isEqualTo(HighSchoolType.SPECIALIZED);
    }

    private EvaluationRule rule(Long id, String recruitmentUnit) {
        EvaluationRule rule = org.mockito.Mockito.mock(EvaluationRule.class);
        org.mockito.Mockito.lenient().when(rule.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(rule.getName()).thenReturn("테스트 규칙 " + id);
        org.mockito.Mockito.lenient().when(rule.getVersion()).thenReturn(1);
        when(rule.getAdmissionType()).thenReturn("학생부 교과");
        when(rule.getRecruitmentUnit()).thenReturn(recruitmentUnit);
        return rule;
    }
}
