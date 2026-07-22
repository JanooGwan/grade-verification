package com.jinhakapply.gradevalidation.admission.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.DUPLICATE_ADMISSION_TRACK;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.DUPLICATE_RECRUITMENT_UNIT;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_STUDENT_APPLICATION;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.STUDENT_APPLICATION_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.jinhakapply.gradevalidation.admission.domain.AdmissionTrack;
import com.jinhakapply.gradevalidation.admission.domain.RecruitmentUnit;
import com.jinhakapply.gradevalidation.admission.domain.StudentApplication;
import com.jinhakapply.gradevalidation.admission.dto.CreateAdmissionTrackRequest;
import com.jinhakapply.gradevalidation.admission.dto.CreateRecruitmentUnitRequest;
import com.jinhakapply.gradevalidation.admission.dto.CreateStudentApplicationRequest;
import com.jinhakapply.gradevalidation.admission.repository.AdmissionTrackRepository;
import com.jinhakapply.gradevalidation.admission.repository.RecruitmentUnitRepository;
import com.jinhakapply.gradevalidation.admission.repository.StudentApplicationRepository;
import com.jinhakapply.gradevalidation.admission.repository.VerificationRunRepository;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptCourseRepository;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdmissionCatalogServiceTest {

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

    private AdmissionService service;

    @BeforeEach
    void setUp() {
        service = new AdmissionService(
            trackRepository, unitRepository, applicationRepository, universityRepository,
            studentRepository, courseRepository, ruleRepository, evaluationService,
            verificationRunRepository, objectMapper, verificationResultExcelWriter
        );
    }

    @Test
    void createsTrackWithTrimmedName() {
        University university = university();
        when(universityRepository.findById(1L)).thenReturn(Optional.of(university));
        when(trackRepository.save(any(AdmissionTrack.class))).thenAnswer(invocation -> {
            AdmissionTrack saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });

        var response = service.createTrack(new CreateAdmissionTrackRequest(1L, 2027, " 학생부교과 "));

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.name()).isEqualTo("학생부교과");
        assertThat(response.universityId()).isEqualTo(1L);
        assertThat(response.active()).isTrue();
    }

    @Test
    void rejectsDuplicateTrackBeforeLoadingUniversity() {
        when(trackRepository.existsByUniversityIdAndAdmissionYearAndName(1L, 2027, "학생부교과"))
            .thenReturn(true);

        assertThatThrownBy(() -> service.createTrack(
            new CreateAdmissionTrackRequest(1L, 2027, "학생부교과")
        )).isInstanceOfSatisfying(CustomException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(DUPLICATE_ADMISSION_TRACK));
        verify(universityRepository, never()).findById(any());
    }

    @Test
    void normalizesRecruitmentUnitCodeToUppercase() {
        AdmissionTrack track = track(university());
        when(trackRepository.findById(2L)).thenReturn(Optional.of(track));
        when(unitRepository.save(any(RecruitmentUnit.class))).thenAnswer(invocation -> {
            RecruitmentUnit saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 3L);
            return saved;
        });

        var response = service.createUnit(2L, new CreateRecruitmentUnitRequest(" cs01 ", " 컴퓨터공학부 "));

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.code()).isEqualTo("CS01");
        assertThat(response.name()).isEqualTo("컴퓨터공학부");
    }

    @Test
    void rejectsDuplicateRecruitmentUnitCode() {
        when(trackRepository.findById(2L)).thenReturn(Optional.of(track(university())));
        when(unitRepository.existsByAdmissionTrackIdAndCode(2L, "CS01")).thenReturn(true);

        assertThatThrownBy(() -> service.createUnit(
            2L, new CreateRecruitmentUnitRequest("cs01", "컴퓨터공학부")
        )).isInstanceOfSatisfying(CustomException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(DUPLICATE_RECRUITMENT_UNIT));
        verify(unitRepository, never()).save(any());
    }

    @Test
    void rejectsApplicationWhenStudentAndTrackAdmissionYearsDiffer() {
        Student student = Student.create(2026, "A-001", "학생", null, null, 2026);
        ReflectionTestUtils.setField(student, "id", 10L);
        RecruitmentUnit unit = unit(track(university()));
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student));
        when(unitRepository.findById(3L)).thenReturn(Optional.of(unit));

        assertThatThrownBy(() -> service.createApplication(10L, new CreateStudentApplicationRequest(3L)))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(INVALID_STUDENT_APPLICATION));
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void refusesToDeleteApplicationOwnedByAnotherStudent() {
        Student owner = Student.create(2027, "A-002", "다른 학생", null, null, 2027);
        ReflectionTestUtils.setField(owner, "id", 11L);
        StudentApplication application = StudentApplication.create(owner, unit(track(university())));
        ReflectionTestUtils.setField(application, "id", 20L);
        when(applicationRepository.findOneById(20L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.deleteApplication(10L, 20L))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(STUDENT_APPLICATION_NOT_FOUND));
        verify(applicationRepository, never()).delete(any());
    }

    private University university() {
        University university = University.create("TUK", "한국공학대학교");
        ReflectionTestUtils.setField(university, "id", 1L);
        return university;
    }

    private AdmissionTrack track(University university) {
        AdmissionTrack track = AdmissionTrack.create(university, 2027, "학생부교과");
        ReflectionTestUtils.setField(track, "id", 2L);
        return track;
    }

    private RecruitmentUnit unit(AdmissionTrack track) {
        RecruitmentUnit unit = RecruitmentUnit.create(track, "CS01", "컴퓨터공학부");
        ReflectionTestUtils.setField(unit, "id", 3L);
        return unit;
    }
}
