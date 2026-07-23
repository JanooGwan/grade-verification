package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;
import static com.jinhakapply.gradevalidation.global.util.TextNormalizer.normalizePolicyText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.VerifyGradeRequest;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class TranscriptBatchVerificationService {
    private static final String HANSHIN_UNIVERSITY_CODE = "HS";
    private static final Set<String> COMMON_UNIT_NAMES = Set.of(
        "전체", "전체모집단위", "전모집단위", "전체모집학과", "전체학과", "전학과",
        "공통", "모든모집단위"
    );
    private static final Set<SubjectCategory> HANSHIN_SUBJECTS = Set.of(
        SubjectCategory.KOREAN,
        SubjectCategory.MATH,
        SubjectCategory.ENGLISH,
        SubjectCategory.SOCIAL,
        SubjectCategory.SCIENCE
    );

    private final EvaluationRuleRepository ruleRepository;
    private final EvaluationService evaluationService;

    TranscriptBatchVerificationResult verify(
        Long universityId,
        int admissionYear,
        List<TransferApplicationRow> applications,
        List<TranscriptExcelRow> courses
    ) {
        if (universityId == null) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "성적 검증 대상 대학교를 선택해 주세요.");
        }
        List<EvaluationRule> rules = ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            universityId, admissionYear, EvaluationRuleStatus.PUBLISHED
        );
        Map<String, List<TranscriptExcelRow>> coursesByApplicant = courses.stream()
            .filter(course -> HANSHIN_SUBJECTS.contains(course.subjectCategory()))
            .collect(Collectors.groupingBy(TranscriptExcelRow::applicantNumber));

        List<TranscriptBatchVerificationResult.Success> successes = new ArrayList<>();
        List<TranscriptBatchVerificationResult.Failure> failures = new ArrayList<>();
        for (TransferApplicationRow application : applications) {
            List<TranscriptExcelRow> applicantCourses = coursesByApplicant.getOrDefault(
                application.applicantNumber(), List.of()
            );
            String studentName = applicantCourses.isEmpty() ? "미등록" : applicantCourses.getFirst().studentName();
            List<EvaluationRule> matchedRules = matchRules(rules, application);
            if (matchedRules.isEmpty()) {
                failures.add(failure(application, studentName, applicantCourses.size(),
                    "RULE_NOT_FOUND", "전형·모집단위에 맞는 게시 규칙이 없습니다."));
                continue;
            }
            if (matchedRules.size() > 1) {
                failures.add(failure(application, studentName, applicantCourses.size(),
                    "RULE_CONFLICT", "적용 가능한 게시 규칙이 여러 개입니다: " +
                        matchedRules.stream().map(rule -> "#" + rule.getId()).collect(Collectors.joining(", "))));
                continue;
            }
            if (applicantCourses.isEmpty()) {
                failures.add(failure(application, studentName, 0,
                    "COURSE_NOT_FOUND", "국어·영어·수학·사회·과학·한국사 성적이 없습니다."));
                continue;
            }

            EvaluationRule rule = matchedRules.getFirst();
            boolean graduated = application.graduationYear() != null
                && application.graduationYear() < admissionYear;
            VerifyGradeRequest request = new VerifyGradeRequest(
                rule.getId(), graduated, HighSchoolType.GENERAL, application.graduationYear(),
                applicantCourses.stream().map(this::toCourseGrade).toList()
            );
            try {
                GradeVerificationResponse verification = evaluationService.verify(rule, request);
                List<TranscriptBatchVerificationResult.SelectedCourse> selected = new ArrayList<>();
                for (int index = 0; index < verification.calculations().size(); index++) {
                    GradeVerificationResponse.CourseCalculation calculation = verification.calculations().get(index);
                    if (calculation.included()) {
                        selected.add(new TranscriptBatchVerificationResult.SelectedCourse(
                            applicantCourses.get(index), calculation
                        ));
                    }
                }
                successes.add(new TranscriptBatchVerificationResult.Success(
                    application, studentName, compact(verification), List.copyOf(selected)
                ));
            } catch (CustomException exception) {
                failures.add(failure(application, studentName, applicantCourses.size(),
                    exception.getErrorCode().getCode(), exception.getFullMessage()));
            }
        }
        return new TranscriptBatchVerificationResult(List.copyOf(successes), List.copyOf(failures));
    }

    private GradeVerificationResponse compact(GradeVerificationResponse result) {
        return new GradeVerificationResponse(
            result.ruleId(), result.ruleName(), result.ruleVersion(), result.universityName(),
            result.admissionType(), result.recruitmentUnit(), result.finalScore(), result.baseScore(),
            result.averageGrade(), result.selectionStrategy(), result.scoreAggregation(), result.sourceDocument(),
            result.sourcePages(), result.includedCourseCount(), result.excludedCourseCount(),
            result.calculationSummary(), List.of(), result.warnings()
        );
    }

    private List<EvaluationRule> matchRules(List<EvaluationRule> rules, TransferApplicationRow application) {
        List<EvaluationRule> sameTrack = rules.stream()
            .filter(rule -> normalizePolicyText(rule.getAdmissionType())
                .equals(normalizePolicyText(application.admissionTrackName())))
            .toList();
        if (sameTrack.isEmpty()) {
            sameTrack = rules.stream().filter(this::isHanshinCommonGradeRule).toList();
        }
        List<EvaluationRule> exact = sameTrack.stream()
            .filter(rule -> normalizePolicyText(rule.getRecruitmentUnit())
                .equals(normalizePolicyText(application.recruitmentUnitName())))
            .toList();
        if (!exact.isEmpty()) return exact;
        return sameTrack.stream()
            .filter(rule -> COMMON_UNIT_NAMES.contains(normalizePolicyText(rule.getRecruitmentUnit())))
            .toList();
    }

    private boolean isHanshinCommonGradeRule(EvaluationRule rule) {
        return HANSHIN_UNIVERSITY_CODE.equals(rule.getUniversity().getCode())
            && "학생부교과".equals(normalizePolicyText(rule.getAdmissionType()))
            && COMMON_UNIT_NAMES.contains(normalizePolicyText(rule.getRecruitmentUnit()));
    }

    private VerifyGradeRequest.CourseGrade toCourseGrade(TranscriptExcelRow course) {
        return new VerifyGradeRequest.CourseGrade(
            course.schoolYear(), course.semester(), course.subjectCategory(), course.courseName(),
            course.grade(), course.gradeScale(), course.achievement(), course.rawScore(), course.meanScore(),
            course.standardDeviation(), course.studentCount(), course.rankPosition(), course.tiedRankCount(),
            course.legacyAchievement(), course.careerSubject(), course.professionalCourse(), course.credits()
        );
    }

    private TranscriptBatchVerificationResult.Failure failure(
        TransferApplicationRow application,
        String studentName,
        int courseCount,
        String code,
        String reason
    ) {
        return new TranscriptBatchVerificationResult.Failure(
            application, studentName, courseCount, code, reason
        );
    }
}
