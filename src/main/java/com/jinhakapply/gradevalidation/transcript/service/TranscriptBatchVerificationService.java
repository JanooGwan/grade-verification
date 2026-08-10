package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;
import static com.jinhakapply.gradevalidation.global.util.TextNormalizer.normalizePolicyText;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.VerifyGradeRequest;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
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
    private final EvaluationRuleRepository ruleRepository;
    private final EvaluationService evaluationService;

    TranscriptBatchVerificationResult verify(
        Long universityId,
        int admissionYear,
        List<TransferApplicationRow> applications,
        List<TranscriptExcelRow> courses
    ) {
        return verify(universityId, admissionYear, applications, courses, Map.of());
    }

    TranscriptBatchVerificationResult verify(
        Long universityId,
        int admissionYear,
        List<TransferApplicationRow> applications,
        List<TranscriptExcelRow> courses,
        Map<String, ApplicantSchoolInfoRow> schoolInfoByApplicant
    ) {
        return verify(
            universityId,
            admissionYear,
            applications,
            courses,
            schoolInfoByApplicant,
            (application, verification) -> { }
        );
    }

    TranscriptBatchVerificationResult verify(
        Long universityId,
        int admissionYear,
        List<TransferApplicationRow> applications,
        List<TranscriptExcelRow> courses,
        Map<String, ApplicantSchoolInfoRow> schoolInfoByApplicant,
        BiConsumer<TransferApplicationRow, GradeVerificationResponse> verifiedResultConsumer
    ) {
        if (universityId == null) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "성적 검증 대상 대학교를 선택해 주세요.");
        }
        List<EvaluationRule> rules = ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            universityId, admissionYear, EvaluationRuleStatus.PUBLISHED
        );
        Map<String, List<TranscriptExcelRow>> coursesByApplicant = courses.stream()
            .collect(Collectors.groupingBy(TranscriptExcelRow::applicantNumber));

        List<TranscriptBatchVerificationResult.Success> successes = new ArrayList<>();
        List<TranscriptBatchVerificationResult.Failure> failures = new ArrayList<>();
        for (TransferApplicationRow application : applications) {
            List<TranscriptExcelRow> applicantCourses = coursesByApplicant.getOrDefault(
                application.applicantNumber(), List.of()
            );
            String studentName = applicantCourses.isEmpty() ? "미등록" : applicantCourses.getFirst().studentName();
            List<TranscriptExcelRow> gradableCourses = applicantCourses.stream()
                .filter(this::hasGradableAssessment)
                .toList();
            ApplicantSchoolInfoRow schoolInfo = schoolInfoByApplicant.get(application.applicantNumber());
            List<EvaluationRule> matchedRules = matchRules(rules, application);
            if (matchedRules.isEmpty()) {
                failures.add(failure(application, studentName, gradableCourses.size(),
                    "RULE_NOT_FOUND", "전형·모집단위에 맞는 게시 규칙이 없습니다."));
                continue;
            }
            if (matchedRules.size() > 1) {
                failures.add(failure(application, studentName, gradableCourses.size(),
                    "RULE_CONFLICT", "적용 가능한 게시 규칙이 여러 개입니다: " +
                        matchedRules.stream().map(rule -> "#" + rule.getId()).collect(Collectors.joining(", "))));
                continue;
            }
            if (schoolInfo != null
                && schoolInfo.educationBackground() != EducationBackground.DOMESTIC_HIGH_SCHOOL) {
                failures.add(failure(application, studentName, gradableCourses.size(),
                    "ALTERNATIVE_ACADEMIC_INPUT_REQUIRED",
                    schoolInfo.educationBackground() == EducationBackground.GED
                        ? "검정고시 출신자는 전 과목 평균점수가 필요하여 학생부 교과목 파일만으로 환산할 수 없습니다."
                        : "외국고 출신자는 전형별 대체 환산 입력이 필요하여 학생부 교과목 파일만으로 환산할 수 없습니다."));
                continue;
            }
            EvaluationRule rule = matchedRules.getFirst();
            if (isSpecializedGraduateTrack(application)
                && (schoolInfo == null || schoolInfo.applicantHighSchoolCategoryCode() == null
                    || schoolInfo.applicantHighSchoolCategoryCode().isBlank())) {
                failures.add(failure(application, studentName, gradableCourses.size(),
                    "SCHOOL_INFO_REQUIRED",
                    "특성화고교졸업자 전형은 지원자격 확인을 위한 지원자 추가정보 파일이 필요합니다."));
                continue;
            }
            if (isIneligibleSpecializedGraduateApplicant(application, schoolInfo)) {
                GradeVerificationResponse verification = ineligibleVerification(
                    rule, application, gradableCourses.size()
                );
                verifiedResultConsumer.accept(application, verification);
                successes.add(new TranscriptBatchVerificationResult.Success(
                    application, studentName, verification, List.of(), schoolInfo
                ));
                continue;
            }
            if (gradableCourses.isEmpty()) {
                failures.add(failure(application, studentName, 0,
                    "COURSE_NOT_FOUND", "국어·영어·수학·사회·과학·한국사 성적이 없습니다."));
                continue;
            }

            Integer graduationYear = schoolInfo != null && schoolInfo.graduationYear() != null
                ? schoolInfo.graduationYear() : application.graduationYear();
            boolean graduated = graduationYear != null && graduationYear < admissionYear;
            HighSchoolType highSchoolType = schoolInfo == null
                ? HighSchoolType.GENERAL : schoolInfo.highSchoolType();
            VerifyGradeRequest request = new VerifyGradeRequest(
                rule.getId(), graduated, highSchoolType, graduationYear,
                gradableCourses.stream().map(this::toCourseGrade).toList()
            );
            try {
                GradeVerificationResponse verification = evaluationService.verify(rule, request);
                GradeVerificationResponse annotated = annotate(verification, application, admissionYear);
                verifiedResultConsumer.accept(application, annotated);
                List<TranscriptBatchVerificationResult.SelectedCourse> selected = new ArrayList<>();
                for (int index = 0; index < verification.calculations().size(); index++) {
                    GradeVerificationResponse.CourseCalculation calculation = verification.calculations().get(index);
                    if (calculation.included()) {
                        selected.add(new TranscriptBatchVerificationResult.SelectedCourse(
                            gradableCourses.get(index), calculation
                        ));
                    }
                }
                successes.add(new TranscriptBatchVerificationResult.Success(
                    application, studentName, compact(annotated),
                    List.copyOf(selected), schoolInfo
                ));
            } catch (CustomException exception) {
                failures.add(failure(application, studentName, gradableCourses.size(),
                    exception.getErrorCode().getCode(), exception.getFullMessage()));
            }
        }
        return new TranscriptBatchVerificationResult(List.copyOf(successes), List.copyOf(failures));
    }

    private boolean isIneligibleSpecializedGraduateApplicant(
        TransferApplicationRow application,
        ApplicantSchoolInfoRow schoolInfo
    ) {
        if (!isSpecializedGraduateTrack(application)) return false;
        return schoolInfo != null
            && !"전문계고교".equals(normalizePolicyText(schoolInfo.applicantHighSchoolCategoryCode()));
    }

    private boolean isSpecializedGraduateTrack(TransferApplicationRow application) {
        return normalizePolicyText(application.admissionTrackName()).contains("특성화고교졸업자");
    }

    private GradeVerificationResponse ineligibleVerification(
        EvaluationRule rule,
        TransferApplicationRow application,
        int excludedCourseCount
    ) {
        BigDecimal zero = BigDecimal.ZERO;
        GradeVerificationResponse.CalculationSummary summary =
            new GradeVerificationResponse.CalculationSummary(
                "지원자 고교구분코드가 전문계고교가 아니므로 지원자격 미달로 성적을 산출하지 않습니다.",
                zero, zero, zero, zero, zero, zero, null, zero, rule.getScoreMultiplier(), zero,
                rule.getIntermediateScale(), rule.getIntermediateRounding(),
                rule.getFinalScale(), rule.getFinalRounding(), Map.of()
            );
        return new GradeVerificationResponse(
            rule.getId(), rule.getName(), rule.getVersion(),
            rule.getUniversity() == null ? null : rule.getUniversity().getName(),
            application.admissionTrackName(), application.recruitmentUnitName(),
            zero, zero, null, rule.getSelectionStrategy(), rule.getScoreAggregation(),
            rule.getSourceDocument(), rule.getSourcePages(), 0, excludedCourseCount, summary,
            List.of(), List.of(
                "특성화고교졸업자전형 지원자이나 지원자 고교구분코드가 전문계고교가 아니므로 0점 처리했습니다."
            )
        );
    }

    private GradeVerificationResponse annotate(
        GradeVerificationResponse result,
        TransferApplicationRow application,
        int admissionYear
    ) {
        List<String> warnings = new ArrayList<>(result.warnings());
        String track = normalizePolicyText(application.admissionTrackName());
        if (track.contains("참인재")) {
            warnings.add("교과 540점만 산출했습니다. 출결 60점과 면접 400점은 전달양식에 없어 포함하지 않았습니다.");
        } else if (track.contains("논술")) {
            warnings.add("학생부교과 200점만 산출했습니다. 논술고사 800점은 전달양식에 없어 포함하지 않았습니다.");
        } else if (track.contains("체육실기")) {
            warnings.add(admissionYear == 2026
                ? "학생부교과 600점만 산출했습니다. 체육실기 400점은 전달양식에 없어 포함하지 않았습니다."
                : "학생부교과 450점만 산출했습니다. 체육실기 550점은 전달양식에 없어 포함하지 않았습니다.");
        }
        warnings.add("학교폭력 조치사항 감점은 전달양식에 없어 포함하지 않았습니다.");
        return new GradeVerificationResponse(
            result.ruleId(), result.ruleName(), result.ruleVersion(), result.universityName(),
            result.admissionType(), result.recruitmentUnit(), result.finalScore(), result.baseScore(),
            result.averageGrade(), result.selectionStrategy(), result.scoreAggregation(), result.sourceDocument(),
            result.sourcePages(), result.includedCourseCount(), result.excludedCourseCount(),
            result.calculationSummary(), result.calculations(), List.copyOf(warnings)
        );
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
            if (requiresDedicatedHanshinRule(application.admissionTrackName())) return List.of();
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

    private boolean requiresDedicatedHanshinRule(String admissionTrackName) {
        String track = normalizePolicyText(admissionTrackName);
        return track.contains("참인재")
            || track.contains("논술")
            || track.contains("체육실기")
            || track.contains("특성화고교졸업자");
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

    private boolean hasGradableAssessment(TranscriptExcelRow course) {
        return course.grade() != null
            || course.achievement() != null
            || course.rankPosition() != null
            || course.legacyAchievement() != null;
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
