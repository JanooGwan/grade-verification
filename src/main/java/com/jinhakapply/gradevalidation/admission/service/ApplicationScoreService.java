package com.jinhakapply.gradevalidation.admission.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.CONFLICTING_EVALUATION_RULES;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.MATCHING_EVALUATION_RULE_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.STUDENT_APPLICATION_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.util.TextNormalizer.normalizePolicyText;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreRun;
import com.jinhakapply.gradevalidation.admission.domain.ApplicationScoreResult;
import com.jinhakapply.gradevalidation.admission.domain.StudentCommonEvaluationSnapshot;
import com.jinhakapply.gradevalidation.admission.domain.RecruitmentUnit;
import com.jinhakapply.gradevalidation.admission.domain.StudentApplication;
import com.jinhakapply.gradevalidation.admission.dto.ApplicationScoreResponse;
import com.jinhakapply.gradevalidation.admission.dto.CalculateApplicationScoreRequest;
import com.jinhakapply.gradevalidation.admission.repository.ApplicationScoreRunRepository;
import com.jinhakapply.gradevalidation.admission.repository.StudentApplicationRepository;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.VerifyGradeRequest;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptCourse;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptCourseRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentAttendanceRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentSchoolViolenceActionRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentGedSubjectScoreRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentLegacyGradeSummaryRepository;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationScoreService {
    private static final Set<String> COMMON_UNIT_NAMES = Set.of(
        "전체", "전체모집단위", "전모집단위", "전체모집학과", "전체학과", "전학과",
        "공통", "모든모집단위"
    );

    private final StudentApplicationRepository applicationRepository;
    private final StudentTranscriptCourseRepository courseRepository;
    private final EvaluationRuleRepository ruleRepository;
    private final EvaluationService evaluationService;
    private final ApplicationScoreRunRepository scoreRunRepository;
    private final List<QuantitativeScoreCalculator> calculators;
    private final ObjectMapper objectMapper;
    private final StudentAttendanceRepository attendanceRepository;
    private final StudentSchoolViolenceActionRepository schoolViolenceRepository;
    private final StudentGedSubjectScoreRepository gedSubjectScoreRepository;
    private final StudentLegacyGradeSummaryRepository legacyGradeSummaryRepository;

    @Transactional
    public ApplicationScoreResponse calculate(
        Long studentId,
        Long applicationId,
        CalculateApplicationScoreRequest request
    ) {
        StudentApplication application = applicationRepository.findOneById(applicationId)
            .orElseThrow(() -> CustomException.of(STUDENT_APPLICATION_NOT_FOUND));
        if (!application.getStudent().getId().equals(studentId)) {
            throw CustomException.of(STUDENT_APPLICATION_NOT_FOUND);
        }
        EvaluationRule rule = matchingRule(application);
        StudentCommonEvaluationSnapshot commonData = commonData(application);

        GradeVerificationResponse gradeVerification = null;
        if (commonData.educationBackground() == EducationBackground.DOMESTIC_HIGH_SCHOOL) {
            List<StudentTranscriptCourse> courses = courseRepository
                .findAllByStudent_IdOrderBySchoolYearAscSemesterAscCourseNameAsc(studentId);
            List<VerifyGradeRequest.CourseGrade> gradeCourses = new java.util.ArrayList<>(
                courses.stream().map(this::toCourseGrade).toList()
            );
            commonData.legacyGradeSummaries().stream().map(this::toCourseGrade).forEach(gradeCourses::add);
            VerifyGradeRequest gradeRequest = new VerifyGradeRequest(
                rule.getId(),
                commonData.graduationStatus() == GraduationStatus.GRADUATE,
                commonData.highSchoolType(),
                commonData.graduationYear(),
                gradeCourses
            );
            gradeVerification = evaluationService.verify(gradeRequest);
        }

        var track = application.getRecruitmentUnit().getAdmissionTrack();
        QuantitativeScoreCalculator calculator = calculators.stream()
            .filter(candidate -> candidate.supports(rule))
            .findFirst()
            .orElseThrow(() -> CustomException.of(
                com.jinhakapply.gradevalidation.global.code.ApiResponseCode.APPLICATION_SCORE_POLICY_NOT_FOUND));
        ApplicationScoreResult result = calculator.calculate(
            rule, track.getName(), gradeVerification, request, commonData
        );
        StoredApplicationScore storedResult = new StoredApplicationScore(request, commonData, result, gradeVerification);
        ApplicationScoreRun run = scoreRunRepository.save(ApplicationScoreRun.create(
            application, rule, commonData.educationBackground(), result, objectMapper.writeValueAsString(storedResult)
        ));
        return ApplicationScoreResponse.from(run, result, gradeVerification);
    }

    private EvaluationRule matchingRule(StudentApplication application) {
        RecruitmentUnit unit = application.getRecruitmentUnit();
        var track = unit.getAdmissionTrack();
        List<EvaluationRule> sameTrack = ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
                track.getUniversity().getId(), track.getAdmissionYear(), EvaluationRuleStatus.PUBLISHED)
            .stream()
            .filter(rule -> normalizePolicyText(rule.getAdmissionType()).equals(normalizePolicyText(track.getName())))
            .toList();
        List<EvaluationRule> exact = sameTrack.stream()
            .filter(rule -> normalizePolicyText(rule.getRecruitmentUnit()).equals(normalizePolicyText(unit.getName())))
            .toList();
        List<EvaluationRule> candidates = exact.isEmpty()
            ? sameTrack.stream()
                .filter(rule -> COMMON_UNIT_NAMES.contains(normalizePolicyText(rule.getRecruitmentUnit())))
                .toList()
            : exact;
        if (candidates.isEmpty()) throw CustomException.of(MATCHING_EVALUATION_RULE_NOT_FOUND);
        if (candidates.size() > 1) {
            throw CustomException.of(CONFLICTING_EVALUATION_RULES,
                candidates.stream().map(rule -> "#" + rule.getId() + " " + rule.getName())
                    .collect(Collectors.joining(", ")));
        }
        return candidates.getFirst();
    }

    private VerifyGradeRequest.CourseGrade toCourseGrade(StudentTranscriptCourse course) {
        return new VerifyGradeRequest.CourseGrade(
            course.getSchoolYear(), course.getSemester(), course.getSubjectCategory(), course.getCourseName(),
            course.getGrade(), course.getGradeScale(), course.getAchievement(), course.getRawScore(), course.getMeanScore(),
            course.getStandardDeviation(), course.getStudentCount(), course.getRankPosition(), course.getTiedRankCount(),
            course.getLegacyAchievement(), course.isCareerSubject(), course.isProfessionalCourse(), course.getCredits()
        );
    }

    private VerifyGradeRequest.CourseGrade toCourseGrade(StudentCommonEvaluationSnapshot.LegacyGradeSummary summary) {
        int semester = summary.semester() == null ? 1 : summary.semester();
        String name = summary.summaryType() == com.jinhakapply.gradevalidation.transcript.domain.LegacySummaryType.YEAR
            ? summary.schoolYear() + "학년 석차 요약"
            : summary.schoolYear() + "학년 " + semester + "학기 계열석차 요약";
        return new VerifyGradeRequest.CourseGrade(
            summary.schoolYear(), semester, SubjectCategory.OTHER, name, null, GradeScale.LEGACY,
            null, null, null, null, summary.cohortSize(), summary.rankPosition(), summary.tiedRankCount(),
            null, false, false, summary.credits()
        );
    }

    private StudentCommonEvaluationSnapshot commonData(StudentApplication application) {
        var student = application.getStudent();
        var attendance = attendanceRepository.findAllByStudent_IdOrderBySchoolYearAsc(student.getId()).stream()
            .map(item -> new StudentCommonEvaluationSnapshot.Attendance(
                item.getSchoolYear(), item.getUnexcusedAbsenceDays(), item.getUnexcusedTardyCount(),
                item.getUnexcusedEarlyLeaveCount(), item.getUnexcusedClassAbsenceCount()
            )).toList();
        var actions = schoolViolenceRepository
            .findAllByStudent_IdOrderBySchoolYearAscActionNumberAsc(student.getId()).stream()
            .map(item -> new StudentCommonEvaluationSnapshot.SchoolViolenceAction(
                item.getSchoolYear(), item.getActionNumber(), item.getActionDate(), item.isActive(), item.getNote()
            )).toList();
        var gedScores = gedSubjectScoreRepository.findAllByStudent_IdOrderBySubjectTypeAscSubjectNameAsc(student.getId())
            .stream().map(item -> new StudentCommonEvaluationSnapshot.GedSubjectScore(
                item.getSubjectType(), item.getSubjectName(), item.getScore()
            )).toList();
        var legacySummaries = legacyGradeSummaryRepository.findAllByStudent_IdOrderBySchoolYearAscSemesterAsc(student.getId())
            .stream().map(item -> new StudentCommonEvaluationSnapshot.LegacyGradeSummary(
                item.getSummaryType(), item.getSchoolYear(), item.getSemester(), item.getRankPosition(),
                item.getTiedRankCount(), item.getCohortSize(), item.getCredits()
            )).toList();
        return new StudentCommonEvaluationSnapshot(
            student.getEducationBackground(), student.getHighSchoolType(), student.getGraduationStatus(),
            student.getGraduationYear(), student.getGedAverageScore(), gedScores, legacySummaries, attendance, actions
        );
    }

    private record StoredApplicationScore(
        CalculateApplicationScoreRequest request,
        StudentCommonEvaluationSnapshot commonData,
        ApplicationScoreResult result,
        GradeVerificationResponse gradeVerification
    ) {
    }
}
