package com.jinhakapply.gradevalidation.admission.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.CONFLICTING_EVALUATION_RULES;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.STUDENT_APPLICATION_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.jinhakapply.gradevalidation.admission.domain.AdmissionTrack;
import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreResult;
import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreRun;
import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreStatus;
import com.jinhakapply.gradevalidation.admission.domain.RecruitmentUnit;
import com.jinhakapply.gradevalidation.admission.domain.StudentApplication;
import com.jinhakapply.gradevalidation.admission.dto.CalculateApplicationScoreRequest;
import com.jinhakapply.gradevalidation.admission.repository.ApplicationScoreRunRepository;
import com.jinhakapply.gradevalidation.admission.repository.StudentApplicationRepository;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.repository.StudentAttendanceRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentGedSubjectScoreRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentLegacyGradeSummaryRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentSchoolViolenceActionRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptCourseRepository;
import com.jinhakapply.gradevalidation.university.domain.University;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ApplicationScoreServiceTest {

    @Mock StudentApplicationRepository applicationRepository;
    @Mock StudentTranscriptCourseRepository courseRepository;
    @Mock EvaluationRuleRepository ruleRepository;
    @Mock EvaluationService evaluationService;
    @Mock ApplicationScoreRunRepository scoreRunRepository;
    @Mock QuantitativeScoreCalculator calculator;
    @Mock ObjectMapper objectMapper;
    @Mock StudentAttendanceRepository attendanceRepository;
    @Mock StudentSchoolViolenceActionRepository schoolViolenceRepository;
    @Mock StudentGedSubjectScoreRepository gedSubjectScoreRepository;
    @Mock StudentLegacyGradeSummaryRepository legacyGradeSummaryRepository;

    private ApplicationScoreService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationScoreService(
            applicationRepository, courseRepository, ruleRepository, evaluationService, scoreRunRepository,
            List.of(calculator), objectMapper, attendanceRepository, schoolViolenceRepository,
            gedSubjectScoreRepository, legacyGradeSummaryRepository
        );
    }

    @Test
    void rejectsScoreCalculationForAnotherStudentsApplication() {
        Student owner = org.mockito.Mockito.mock(Student.class);
        StudentApplication application = org.mockito.Mockito.mock(StudentApplication.class);
        when(applicationRepository.findOneById(20L)).thenReturn(Optional.of(application));
        when(application.getStudent()).thenReturn(owner);
        when(owner.getId()).thenReturn(2L);

        assertThatThrownBy(() -> service.calculate(1L, 20L, request()))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(STUDENT_APPLICATION_NOT_FOUND));
        verify(ruleRepository, never()).findAllByUniversityIdAndAdmissionYearAndStatus(
            anyLong(), anyInt(), any(EvaluationRuleStatus.class)
        );
        verify(scoreRunRepository, never()).save(any());
    }

    @Test
    void calculatesGedScoreWithExactPublishedRuleAndPersistsRun() {
        Student student = student(1L, EducationBackground.GED);
        StudentApplication application = application(student);
        EvaluationRule rule = rule("Computer Science", "Regular Admission", 9L);
        ApplicationScoreResult result = result();

        when(applicationRepository.findOneById(20L)).thenReturn(Optional.of(application));
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(10L, 2027, EvaluationRuleStatus.PUBLISHED))
            .thenReturn(List.of(rule));
        when(calculator.supports(rule)).thenReturn(true);
        when(calculator.calculate(any(), any(), any(), any(), any())).thenReturn(result);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(scoreRunRepository.save(any(ApplicationScoreRun.class))).thenAnswer(invocation -> {
            ApplicationScoreRun run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "id", 30L);
            return run;
        });
        when(attendanceRepository.findAllByStudent_IdOrderBySchoolYearAsc(1L)).thenReturn(List.of());
        when(schoolViolenceRepository.findAllByStudent_IdOrderBySchoolYearAscActionNumberAsc(1L)).thenReturn(List.of());
        when(gedSubjectScoreRepository.findAllByStudent_IdOrderBySubjectTypeAscSubjectNameAsc(1L)).thenReturn(List.of());
        when(legacyGradeSummaryRepository.findAllByStudent_IdOrderBySchoolYearAscSemesterAsc(1L)).thenReturn(List.of());

        var response = service.calculate(1L, 20L, request());

        assertThat(response.scoreRunId()).isEqualTo(30L);
        assertThat(response.ruleId()).isEqualTo(9L);
        assertThat(response.educationBackground()).isEqualTo(EducationBackground.GED);
        assertThat(response.gradeVerification()).isNull();
        assertThat(response.finalScore()).isEqualByComparingTo("750");
        verify(evaluationService, never()).verify(any());
        verify(courseRepository, never()).findAllByStudent_IdOrderBySchoolYearAscSemesterAscCourseNameAsc(any());
        verify(scoreRunRepository).save(any(ApplicationScoreRun.class));
    }

    @Test
    void rejectsAmbiguousPublishedRulesBeforeCalculating() {
        Student student = student(1L, EducationBackground.GED);
        StudentApplication application = application(student);
        EvaluationRule firstRule = rule("Computer Science A", "Regular Admission", 9L);
        EvaluationRule secondRule = rule("Computer Science B", "Regular Admission", 10L);

        when(applicationRepository.findOneById(20L)).thenReturn(Optional.of(application));
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(10L, 2027, EvaluationRuleStatus.PUBLISHED))
            .thenReturn(List.of(firstRule, secondRule));

        assertThatThrownBy(() -> service.calculate(1L, 20L, request()))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CONFLICTING_EVALUATION_RULES));
        verify(calculator, never()).calculate(any(), any(), any(), any(), any());
        verify(scoreRunRepository, never()).save(any());
    }

    private Student student(Long id, EducationBackground educationBackground) {
        Student student = org.mockito.Mockito.mock(Student.class);
        when(student.getId()).thenReturn(id);
        lenient().when(student.getEducationBackground()).thenReturn(educationBackground);
        lenient().when(student.getHighSchoolType()).thenReturn(HighSchoolType.GENERAL);
        lenient().when(student.getGraduationStatus()).thenReturn(GraduationStatus.EXPECTED_GRADUATE);
        lenient().when(student.getGraduationYear()).thenReturn(2027);
        return student;
    }

    private StudentApplication application(Student student) {
        University university = org.mockito.Mockito.mock(University.class);
        AdmissionTrack track = org.mockito.Mockito.mock(AdmissionTrack.class);
        RecruitmentUnit unit = org.mockito.Mockito.mock(RecruitmentUnit.class);
        StudentApplication application = org.mockito.Mockito.mock(StudentApplication.class);
        when(university.getId()).thenReturn(10L);
        lenient().when(university.getName()).thenReturn("Test University");
        when(track.getUniversity()).thenReturn(university);
        when(track.getAdmissionYear()).thenReturn(2027);
        when(track.getName()).thenReturn("Regular Admission");
        when(unit.getAdmissionTrack()).thenReturn(track);
        when(unit.getName()).thenReturn("Computer Science");
        lenient().when(application.getId()).thenReturn(20L);
        when(application.getStudent()).thenReturn(student);
        when(application.getRecruitmentUnit()).thenReturn(unit);
        return application;
    }

    private EvaluationRule rule(String name, String admissionType, Long id) {
        EvaluationRule rule = org.mockito.Mockito.mock(EvaluationRule.class);
        when(rule.getId()).thenReturn(id);
        lenient().when(rule.getName()).thenReturn(name);
        when(rule.getAdmissionType()).thenReturn(admissionType);
        when(rule.getRecruitmentUnit()).thenReturn("Computer Science");
        lenient().when(rule.getVersion()).thenReturn(2);
        return rule;
    }

    private ApplicationScoreResult result() {
        return new ApplicationScoreResult(
            ApplicationScoreStatus.COMPLETE, new BigDecimal("500"), new BigDecimal("500"), 0,
            new BigDecimal("100"), new BigDecimal("150"), BigDecimal.ZERO, new BigDecimal("750"),
            new BigDecimal("750"), new BigDecimal("750"), new BigDecimal("800"), new BigDecimal("800"),
            List.of(), List.of(), List.of(), List.of()
        );
    }

    private CalculateApplicationScoreRequest request() {
        return new CalculateApplicationScoreRequest(new BigDecimal("150"), new BigDecimal("100"));
    }
}
