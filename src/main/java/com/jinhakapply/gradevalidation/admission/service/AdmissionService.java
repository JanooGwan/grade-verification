package com.jinhakapply.gradevalidation.admission.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.ADMISSION_TRACK_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.CONFLICTING_EVALUATION_RULES;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.DUPLICATE_ADMISSION_TRACK;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.DUPLICATE_RECRUITMENT_UNIT;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.DUPLICATE_STUDENT_APPLICATION;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_STUDENT_APPLICATION;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.MATCHING_EVALUATION_RULE_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.RECRUITMENT_UNIT_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.STUDENT_APPLICATION_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.TRANSCRIPT_STUDENT_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.UNIVERSITY_NOT_FOUND;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jinhakapply.gradevalidation.admission.domain.AdmissionTrack;
import com.jinhakapply.gradevalidation.admission.domain.RecruitmentUnit;
import com.jinhakapply.gradevalidation.admission.domain.StudentApplication;
import com.jinhakapply.gradevalidation.admission.domain.VerificationRun;
import com.jinhakapply.gradevalidation.admission.dto.AdmissionTrackResponse;
import com.jinhakapply.gradevalidation.admission.dto.ApplicationVerificationResponse;
import com.jinhakapply.gradevalidation.admission.dto.CreateAdmissionTrackRequest;
import com.jinhakapply.gradevalidation.admission.dto.CreateRecruitmentUnitRequest;
import com.jinhakapply.gradevalidation.admission.dto.CreateStudentApplicationRequest;
import com.jinhakapply.gradevalidation.admission.dto.RecruitmentUnitResponse;
import com.jinhakapply.gradevalidation.admission.dto.RuleMatchResponse;
import com.jinhakapply.gradevalidation.admission.dto.StudentApplicationResponse;
import com.jinhakapply.gradevalidation.admission.dto.UpdateAdmissionTrackRequest;
import com.jinhakapply.gradevalidation.admission.dto.UpdateRecruitmentUnitRequest;
import com.jinhakapply.gradevalidation.admission.dto.VerificationHistoryResponse;
import com.jinhakapply.gradevalidation.admission.dto.VerificationHistoryDetailResponse;
import com.jinhakapply.gradevalidation.admission.repository.AdmissionTrackRepository;
import com.jinhakapply.gradevalidation.admission.repository.RecruitmentUnitRepository;
import com.jinhakapply.gradevalidation.admission.repository.StudentApplicationRepository;
import com.jinhakapply.gradevalidation.admission.repository.VerificationRunRepository;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.dto.VerifyGradeRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptCourse;
import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptCourseRepository;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdmissionService {
    private final AdmissionTrackRepository trackRepository;
    private final RecruitmentUnitRepository unitRepository;
    private final StudentApplicationRepository applicationRepository;
    private final UniversityRepository universityRepository;
    private final StudentRepository studentRepository;
    private final StudentTranscriptCourseRepository courseRepository;
    private final EvaluationRuleRepository ruleRepository;
    private final EvaluationService evaluationService;
    private final VerificationRunRepository verificationRunRepository;
    private final ObjectMapper objectMapper;
    private final EvaluationRuleMatcher evaluationRuleMatcher;
    private final VerificationResultExcelWriter verificationResultExcelWriter;

    @Transactional
    public AdmissionTrackResponse createTrack(CreateAdmissionTrackRequest request) {
        String name = request.name().trim();
        if (trackRepository.existsByUniversityIdAndAdmissionYearAndName(
            request.universityId(), request.admissionYear(), name
        )) {
            throw CustomException.of(DUPLICATE_ADMISSION_TRACK);
        }
        University university = universityRepository.findById(request.universityId())
            .orElseThrow(() -> CustomException.of(UNIVERSITY_NOT_FOUND));
        AdmissionTrack track = trackRepository.save(AdmissionTrack.create(
            university, request.admissionYear(), name
        ));
        return AdmissionTrackResponse.of(track, List.of());
    }

    @Transactional
    public AdmissionTrackResponse updateTrack(Long trackId, UpdateAdmissionTrackRequest request) {
        AdmissionTrack track = findTrack(trackId);
        String name = request.name().trim();
        if (!track.getName().equals(name) && trackRepository.existsByUniversityIdAndAdmissionYearAndName(
            track.getUniversity().getId(), track.getAdmissionYear(), name
        )) {
            throw CustomException.of(DUPLICATE_ADMISSION_TRACK);
        }
        track.update(name, request.active());
        return AdmissionTrackResponse.of(track, unitRepository.findAllByAdmissionTrackIdOrderByNameAsc(trackId));
    }

    public List<AdmissionTrackResponse> findTracks(Long universityId, int admissionYear) {
        List<AdmissionTrack> tracks = trackRepository
            .findAllByUniversityIdAndAdmissionYearOrderByNameAsc(universityId, admissionYear);
        if (tracks.isEmpty()) return List.of();
        Map<Long, List<RecruitmentUnit>> unitsByTrack = unitRepository
            .findAllByAdmissionTrackIdInOrderByNameAsc(tracks.stream().map(AdmissionTrack::getId).toList())
            .stream().collect(Collectors.groupingBy(unit -> unit.getAdmissionTrack().getId()));
        return tracks.stream()
            .map(track -> AdmissionTrackResponse.of(track, unitsByTrack.getOrDefault(track.getId(), List.of())))
            .toList();
    }

    @Transactional
    public RecruitmentUnitResponse createUnit(Long trackId, CreateRecruitmentUnitRequest request) {
        AdmissionTrack track = findTrack(trackId);
        String name = request.name().trim();
        String code = cleanCode(request.code());
        if (unitRepository.existsByAdmissionTrackIdAndName(trackId, name)) {
            throw CustomException.of(DUPLICATE_RECRUITMENT_UNIT);
        }
        if (code != null && unitRepository.existsByAdmissionTrackIdAndCode(trackId, code)) {
            throw CustomException.of(DUPLICATE_RECRUITMENT_UNIT);
        }
        return RecruitmentUnitResponse.from(unitRepository.save(
            RecruitmentUnit.create(track, code, name)
        ));
    }

    @Transactional
    public RecruitmentUnitResponse updateUnit(Long unitId, UpdateRecruitmentUnitRequest request) {
        RecruitmentUnit unit = findUnit(unitId);
        String name = request.name().trim();
        String code = cleanCode(request.code());
        Long trackId = unit.getAdmissionTrack().getId();
        if (!unit.getName().equals(name) && unitRepository.existsByAdmissionTrackIdAndName(
            trackId, name
        )) {
            throw CustomException.of(DUPLICATE_RECRUITMENT_UNIT);
        }
        if (code != null && unitRepository.existsByAdmissionTrackIdAndCodeAndIdNot(trackId, code, unitId)) {
            throw CustomException.of(DUPLICATE_RECRUITMENT_UNIT);
        }
        unit.update(code, name, request.active());
        return RecruitmentUnitResponse.from(unit);
    }

    @Transactional
    public StudentApplicationResponse createApplication(Long studentId, CreateStudentApplicationRequest request) {
        Student student = findStudent(studentId);
        RecruitmentUnit unit = findUnit(request.recruitmentUnitId());
        AdmissionTrack track = unit.getAdmissionTrack();
        if (!track.isActive() || !unit.isActive()) {
            throw CustomException.of(INVALID_STUDENT_APPLICATION, "비활성 전형 또는 모집단위에는 지원할 수 없습니다.");
        }
        if (student.getAdmissionYear() != track.getAdmissionYear()) {
            throw CustomException.of(INVALID_STUDENT_APPLICATION, "학생 모집연도와 전형 모집연도가 다릅니다.");
        }
        if (applicationRepository.existsByStudentIdAndRecruitmentUnitId(studentId, unit.getId())) {
            throw CustomException.of(DUPLICATE_STUDENT_APPLICATION);
        }
        return StudentApplicationResponse.from(applicationRepository.save(
            StudentApplication.create(student, unit)
        ));
    }

    public List<StudentApplicationResponse> findApplications(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw CustomException.of(TRANSCRIPT_STUDENT_NOT_FOUND);
        }
        return applicationRepository.findAllByStudentIdOrderByCreatedAtDesc(studentId).stream()
            .map(StudentApplicationResponse::from)
            .toList();
    }

    @Transactional
    public void deleteApplication(Long studentId, Long applicationId) {
        StudentApplication application = findApplication(applicationId);
        if (!application.getStudent().getId().equals(studentId)) {
            throw CustomException.of(STUDENT_APPLICATION_NOT_FOUND);
        }
        applicationRepository.delete(application);
    }

    public RuleMatchResponse matchRule(Long studentId, Long applicationId) {
        StudentApplication application = ownedApplication(studentId, applicationId);
        List<EvaluationRule> candidates = findMatchingRules(application);
        if (candidates.isEmpty()) return RuleMatchResponse.notFound();
        if (candidates.size() > 1) return RuleMatchResponse.conflict(candidates);
        return RuleMatchResponse.matched(candidates.getFirst());
    }

    @Transactional
    public ApplicationVerificationResponse verifyApplication(Long studentId, Long applicationId) {
        StudentApplication application = ownedApplication(studentId, applicationId);
        List<EvaluationRule> candidates = findMatchingRules(application);
        if (candidates.isEmpty()) throw CustomException.of(MATCHING_EVALUATION_RULE_NOT_FOUND);
        if (candidates.size() > 1) {
            throw CustomException.of(CONFLICTING_EVALUATION_RULES,
                candidates.stream().map(rule -> "#" + rule.getId() + " " + rule.getName()).collect(Collectors.joining(", ")));
        }
        List<StudentTranscriptCourse> courses = courseRepository
            .findAllByStudent_IdOrderBySchoolYearAscSemesterAscCourseNameAsc(studentId);
        VerifyGradeRequest request = new VerifyGradeRequest(
            candidates.getFirst().getId(),
            application.getStudent().getGraduationStatus()
                == com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus.GRADUATE,
            application.getStudent().getHighSchoolType(),
            courses.stream().map(this::toCourseGrade).toList()
        );
        EvaluationRule rule = candidates.getFirst();
        var result = evaluationService.verify(request);
        VerificationRun run = verificationRunRepository.save(VerificationRun.create(
            application, rule, result, objectMapper.writeValueAsString(result)
        ));
        return new ApplicationVerificationResponse(
            run.getId(), run.getCreatedAt(), StudentApplicationResponse.from(application), result
        );
    }

    public List<VerificationHistoryResponse> findVerificationHistory(Long studentId) {
        if (!studentRepository.existsById(studentId)) throw CustomException.of(TRANSCRIPT_STUDENT_NOT_FOUND);
        return verificationRunRepository.findTop50ByStudentIdOrderByCreatedAtDesc(studentId).stream()
            .map(VerificationHistoryResponse::from)
            .toList();
    }

    public VerificationHistoryDetailResponse findVerificationHistoryDetail(Long studentId, Long runId) {
        VerificationRun run = ownedVerificationRun(studentId, runId);
        GradeVerificationResponse result = objectMapper.readValue(run.getResultJson(), GradeVerificationResponse.class);
        return new VerificationHistoryDetailResponse(
            run.getId(), studentId, run.getApplication() == null ? null : run.getApplication().getId(),
            run.getCreatedAt(), result
        );
    }

    public byte[] exportVerificationResult(Long studentId, Long runId) {
        VerificationRun run = ownedVerificationRun(studentId, runId);
        GradeVerificationResponse result = objectMapper.readValue(run.getResultJson(), GradeVerificationResponse.class);
        return verificationResultExcelWriter.write(
            run.getStudent().getApplicantNumber(), run.getStudent().getName(), run.getCreatedAt(), result
        );
    }

    private List<EvaluationRule> findMatchingRules(StudentApplication application) {
        RecruitmentUnit unit = application.getRecruitmentUnit();
        AdmissionTrack track = unit.getAdmissionTrack();
        List<EvaluationRule> sameTrack = ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
                track.getUniversity().getId(), track.getAdmissionYear(), EvaluationRuleStatus.PUBLISHED)
            .stream()
            .filter(rule -> evaluationRuleMatcher.matchesAdmissionType(rule, track.getName(), unit.getName()))
            .toList();
        List<EvaluationRule> exact = sameTrack.stream()
            .filter(rule -> evaluationRuleMatcher.exactlyMatchesRecruitmentUnit(rule, unit.getName()))
            .toList();
        if (!exact.isEmpty()) return exact;
        return sameTrack.stream()
            .filter(rule -> evaluationRuleMatcher.matchesRecruitmentUnit(rule, unit.getName()))
            .toList();
    }

    private VerifyGradeRequest.CourseGrade toCourseGrade(StudentTranscriptCourse course) {
        return new VerifyGradeRequest.CourseGrade(
            course.getSchoolYear(), course.getSemester(), course.getSubjectCategory(), course.getCourseName(),
            course.getGrade(), course.getAchievement(), course.getRawScore(), course.getMeanScore(),
            course.getStandardDeviation(), course.getStudentCount(), course.isCareerSubject(),
            course.isProfessionalCourse(), course.isVocationalTrainingSemester(), course.getCredits()
        );
    }

    private String cleanCode(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private AdmissionTrack findTrack(Long id) {
        return trackRepository.findById(id).orElseThrow(() -> CustomException.of(ADMISSION_TRACK_NOT_FOUND));
    }

    private RecruitmentUnit findUnit(Long id) {
        return unitRepository.findById(id).orElseThrow(() -> CustomException.of(RECRUITMENT_UNIT_NOT_FOUND));
    }

    private Student findStudent(Long id) {
        return studentRepository.findById(id).orElseThrow(() -> CustomException.of(TRANSCRIPT_STUDENT_NOT_FOUND));
    }

    private StudentApplication findApplication(Long id) {
        return applicationRepository.findOneById(id)
            .orElseThrow(() -> CustomException.of(STUDENT_APPLICATION_NOT_FOUND));
    }

    private StudentApplication ownedApplication(Long studentId, Long applicationId) {
        StudentApplication application = findApplication(applicationId);
        if (!application.getStudent().getId().equals(studentId)) {
            throw CustomException.of(STUDENT_APPLICATION_NOT_FOUND);
        }
        return application;
    }

    private VerificationRun ownedVerificationRun(Long studentId, Long runId) {
        VerificationRun run = verificationRunRepository.findOneById(runId)
            .orElseThrow(() -> CustomException.of(com.jinhakapply.gradevalidation.global.code.ApiResponseCode.VERIFICATION_RUN_NOT_FOUND));
        if (!run.getStudent().getId().equals(studentId)) {
            throw CustomException.of(com.jinhakapply.gradevalidation.global.code.ApiResponseCode.VERIFICATION_RUN_NOT_FOUND);
        }
        return run;
    }
}
