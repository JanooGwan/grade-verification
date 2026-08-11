package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.VerifyGradeRequest;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.university.domain.University;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TranscriptBatchVerificationServiceTest {
    @Mock EvaluationRuleRepository ruleRepository;
    @Mock EvaluationService evaluationService;
    @Mock EvaluationRule rule;
    @Mock GradeVerificationResponse verification;
    @Mock University university;

    @Test
    void matchesKbuApplicationToPublishedAdmissionAndUnitGroup() {
        TranscriptBatchVerificationService service = new TranscriptBatchVerificationService(
            ruleRepository, evaluationService
        );
        EvaluationRule generalRule = mock(EvaluationRule.class);
        EvaluationRule healthRule = mock(EvaluationRule.class);
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            5L, 2026, EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of(generalRule, healthRule));
        when(generalRule.getUniversity()).thenReturn(university);
        when(generalRule.getAdmissionType()).thenReturn("수시 일반고");
        when(generalRule.getRecruitmentUnit()).thenReturn("일반학과");
        when(healthRule.getUniversity()).thenReturn(university);
        when(healthRule.getAdmissionType()).thenReturn("수시 일반고");
        when(healthRule.getRecruitmentUnit()).thenReturn("간호·치위생·작업치료·임상병리·물리치료");
        when(university.getCode()).thenReturn("KBOK");
        when(evaluationService.verify(eq(healthRule), org.mockito.ArgumentMatchers.any()))
            .thenReturn(verification);
        when(verification.calculations()).thenReturn(List.of());
        TransferApplicationRow application = new TransferApplicationRow(
            2, 2026, "A-001", "2000", "수시 일반고", "3020", "(주)간호학과", 2026
        );

        TranscriptBatchVerificationResult result = service.verify(
            5L, 2026, List.of(application), List.of(course(3, SubjectCategory.KOREAN, "국어"))
        );

        assertThat(result.successes()).hasSize(1);
        assertThat(result.failures()).isEmpty();
        verify(evaluationService).verify(eq(healthRule), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mapsKbuActualDepartmentNamesToRuleGroups() {
        assertThat(List.of(
            TranscriptBatchVerificationService.kbuRuleUnit("(주)간호학과"),
            TranscriptBatchVerificationService.kbuRuleUnit("(주)항공서비스과"),
            TranscriptBatchVerificationService.kbuRuleUnit("(주)공연예술과"),
            TranscriptBatchVerificationService.kbuRuleUnit("(주)사회복지과")
        )).containsExactly(
            "간호·치위생·작업치료·임상병리·물리치료",
            "항공서비스과·준오헤어디자인과",
            "실용음악과·공연예술과",
            "일반학과"
        );
    }

    @Test
    void aggregatesKbuHealthDepartmentSubjectAveragesAndSelections() {
        TranscriptBatchVerificationService service = new TranscriptBatchVerificationService(
            ruleRepository, evaluationService
        );
        when(rule.getAdmissionYear()).thenReturn(2026);
        when(rule.getUniversity()).thenReturn(university);
        when(university.getCode()).thenReturn("KBOK");
        when(rule.getSubjectPriorities()).thenReturn(Map.of(
            SubjectCategory.SCIENCE, 1,
            SubjectCategory.MATH, 2,
            SubjectCategory.KOREAN, 3,
            SubjectCategory.ENGLISH, 4,
            SubjectCategory.SOCIAL, 5
        ));
        when(verification.selectionStrategy()).thenReturn(SelectionStrategy.TOP_N_SUBJECTS);
        when(verification.calculations()).thenReturn(List.of(
            calculation("국어Ⅰ", 1, 1, SubjectCategory.KOREAN, "2", "2", true),
            calculation("국어Ⅱ", 2, 1, SubjectCategory.KOREAN, "5", "1", true),
            calculation("수학", 1, 1, SubjectCategory.MATH, "2", "3", true),
            calculation("영어", 1, 1, SubjectCategory.ENGLISH, "4", "3", false),
            calculation("사회", 1, 1, SubjectCategory.SOCIAL, "5", "3", false),
            calculation("과학", 1, 1, SubjectCategory.SCIENCE, "1", "3", true)
        ));

        List<TranscriptBatchVerificationResult.IntermediateCalculation> result =
            service.buildKbuIntermediateCalculations(rule, verification);

        assertThat(result).extracting(TranscriptBatchVerificationResult.IntermediateCalculation::groupName)
            .containsExactly("국어", "수학", "사회", "과학", "영어");
        assertThat(result).filteredOn(item -> item.groupName().equals("국어")).singleElement().satisfies(item -> {
            assertThat(item.selected()).isTrue();
            assertThat(item.courseCount()).isEqualTo(2);
            assertThat(item.totalCredits()).isEqualByComparingTo("3");
            assertThat(item.gradeTimesCreditsSum()).isEqualByComparingTo("9");
            assertThat(item.averageGrade()).isEqualByComparingTo("3");
        });
        assertThat(result).filteredOn(TranscriptBatchVerificationResult.IntermediateCalculation::selected)
            .extracting(TranscriptBatchVerificationResult.IntermediateCalculation::groupName)
            .containsExactlyInAnyOrder("국어", "수학", "과학");
    }

    @Test
    void aggregatesKbuGeneralDepartmentSemesterAveragesAndSelections() {
        TranscriptBatchVerificationService service = new TranscriptBatchVerificationService(
            ruleRepository, evaluationService
        );
        when(rule.getAdmissionYear()).thenReturn(2026);
        when(rule.getUniversity()).thenReturn(university);
        when(university.getCode()).thenReturn("KBOK");
        when(verification.selectionStrategy()).thenReturn(SelectionStrategy.TOP_N_SEMESTERS);
        when(verification.calculations()).thenReturn(List.of(
            calculation("국어", 1, 1, SubjectCategory.KOREAN, "4", "2", false),
            calculation("수학", 1, 1, SubjectCategory.MATH, "2", "2", false),
            calculation("영어", 1, 2, SubjectCategory.ENGLISH, "2", "3", true),
            calculation("과학", 2, 1, SubjectCategory.SCIENCE, "1", "3", true),
            calculation("사회", 2, 2, SubjectCategory.SOCIAL, "5", "3", false)
        ));

        List<TranscriptBatchVerificationResult.IntermediateCalculation> result =
            service.buildKbuIntermediateCalculations(rule, verification);

        assertThat(result).extracting(TranscriptBatchVerificationResult.IntermediateCalculation::groupName)
            .containsExactly("1학년 1학기", "1학년 2학기", "2학년 1학기", "2학년 2학기");
        assertThat(result).filteredOn(TranscriptBatchVerificationResult.IntermediateCalculation::selected)
            .extracting(TranscriptBatchVerificationResult.IntermediateCalculation::groupName)
            .containsExactlyInAnyOrder("1학년 2학기", "2학년 1학기");
        assertThat(result).filteredOn(item -> item.groupName().equals("1학년 1학기"))
            .singleElement().satisfies(item -> assertThat(item.averageGrade()).isEqualByComparingTo("3"));
    }

    @Test
    void excludesCoursesWithoutGradableAssessmentBeforeVerification() {
        TranscriptBatchVerificationService service = new TranscriptBatchVerificationService(
            ruleRepository, evaluationService
        );
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            1L, 2027, EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of(rule));
        when(rule.getId()).thenReturn(11L);
        when(rule.getAdmissionType()).thenReturn("학생부교과");
        when(rule.getRecruitmentUnit()).thenReturn("전체 모집단위");
        when(rule.getUniversity()).thenReturn(university);
        when(university.getCode()).thenReturn("HS");
        when(evaluationService.verify(eq(rule), org.mockito.ArgumentMatchers.any())).thenReturn(verification);
        when(verification.calculations()).thenReturn(List.of());

        TransferApplicationRow application = new TransferApplicationRow(
            2, 2027, "A-001", "06", "학생부우수자", "21", "컴퓨터공학과", 2027
        );
        List<TranscriptExcelRow> courses = List.of(
            course(3, SubjectCategory.KOREAN, "국어"),
            course(4, SubjectCategory.SOCIAL, "한국사"),
            course(5, SubjectCategory.OTHER, "미술"),
            ungradedCourse(6, "진로와 직업")
        );

        TranscriptBatchVerificationResult result = service.verify(1L, 2027, List.of(application), courses);

        assertThat(result.successes()).hasSize(1);
        assertThat(result.failures()).isEmpty();
        ArgumentCaptor<VerifyGradeRequest> requestCaptor = ArgumentCaptor.forClass(VerifyGradeRequest.class);
        verify(evaluationService).verify(eq(rule), requestCaptor.capture());
        assertThat(requestCaptor.getValue().courses()).extracting(VerifyGradeRequest.CourseGrade::courseName)
            .containsExactly("국어", "한국사", "미술");
    }

    @Test
    void treatsEarlierGraduationYearAsGraduateForSemesterScope() {
        TranscriptBatchVerificationService service = new TranscriptBatchVerificationService(
            ruleRepository, evaluationService
        );
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            1L, 2027, EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of(rule));
        when(rule.getId()).thenReturn(11L);
        when(rule.getAdmissionType()).thenReturn("학생부우수자");
        when(rule.getRecruitmentUnit()).thenReturn("전체 모집단위");
        when(evaluationService.verify(eq(rule), org.mockito.ArgumentMatchers.any())).thenReturn(verification);
        when(verification.calculations()).thenReturn(List.of());
        TransferApplicationRow application = new TransferApplicationRow(
            2, 2027, "A-001", "01", "학생부우수자", "21", "컴퓨터공학과", 2026
        );

        service.verify(
            1L, 2027, List.of(application), List.of(course(3, SubjectCategory.KOREAN, "국어"))
        );

        ArgumentCaptor<VerifyGradeRequest> requestCaptor = ArgumentCaptor.forClass(VerifyGradeRequest.class);
        verify(evaluationService).verify(eq(rule), requestCaptor.capture());
        assertThat(requestCaptor.getValue().graduated()).isTrue();
        assertThat(requestCaptor.getValue().graduationYear()).isEqualTo(2026);
    }

    @Test
    void appliesSpecializedSchoolPolicyEvenForGeneralAdmissionTrack() {
        TranscriptBatchVerificationService service = new TranscriptBatchVerificationService(
            ruleRepository, evaluationService
        );
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            1L, 2027, EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of(rule));
        when(rule.getId()).thenReturn(11L);
        when(rule.getAdmissionType()).thenReturn("참인재");
        when(rule.getRecruitmentUnit()).thenReturn("전체 모집단위");
        when(evaluationService.verify(eq(rule), org.mockito.ArgumentMatchers.any())).thenReturn(verification);
        when(verification.calculations()).thenReturn(List.of());
        TransferApplicationRow application = new TransferApplicationRow(
            2, 2027, "A-001", "06", "참인재", "21", "컴퓨터공학과", 2027
        );
        ApplicantSchoolInfoRow schoolInfo = new ApplicantSchoolInfoRow(
            2, 2026, "A-001", 2027, "S-001", "직업고등학교", "전문학과",
            "실업고", "특성화고", "전문계고교",
            EducationBackground.DOMESTIC_HIGH_SCHOOL, HighSchoolType.SPECIALIZED
        );

        service.verify(
            1L, 2027, List.of(application),
            List.of(course(3, SubjectCategory.KOREAN, "국어")),
            java.util.Map.of("A-001", schoolInfo)
        );

        ArgumentCaptor<VerifyGradeRequest> requestCaptor = ArgumentCaptor.forClass(VerifyGradeRequest.class);
        verify(evaluationService).verify(eq(rule), requestCaptor.capture());
        assertThat(requestCaptor.getValue().highSchoolType()).isEqualTo(HighSchoolType.SPECIALIZED);
    }

    @Test
    void recordsFailureWhenNoPublishedRuleMatches() {
        TranscriptBatchVerificationService service = new TranscriptBatchVerificationService(
            ruleRepository, evaluationService
        );
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            1L, 2027, EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of());
        TransferApplicationRow application = new TransferApplicationRow(
            2, 2027, "A-001", "06", "학생부교과", "21", "컴퓨터공학과", 2027
        );

        TranscriptBatchVerificationResult result = service.verify(
            1L, 2027, List.of(application), List.of(course(3, SubjectCategory.KOREAN, "국어"))
        );

        assertThat(result.successes()).isEmpty();
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.code()).isEqualTo("RULE_NOT_FOUND");
            assertThat(failure.application().rowNumber()).isEqualTo(2);
        });
    }

    @Test
    void doesNotApplyCommonTimesTenRuleToTalentTrack() {
        TranscriptBatchVerificationService service = new TranscriptBatchVerificationService(
            ruleRepository, evaluationService
        );
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            1L, 2027, EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of(rule));
        when(rule.getAdmissionType()).thenReturn("학생부교과");
        TransferApplicationRow application = new TransferApplicationRow(
            2, 2027, "A-001", "06", "참인재", "21", "컴퓨터공학과", 2027
        );

        TranscriptBatchVerificationResult result = service.verify(
            1L, 2027, List.of(application), List.of(course(3, SubjectCategory.KOREAN, "국어"))
        );

        assertThat(result.successes()).isEmpty();
        assertThat(result.failures()).singleElement()
            .satisfies(failure -> assertThat(failure.code()).isEqualTo("RULE_NOT_FOUND"));
    }

    @Test
    void prefersSpecializedGraduateRuleOverCommonHanshinRule() {
        TranscriptBatchVerificationService service = new TranscriptBatchVerificationService(
            ruleRepository, evaluationService
        );
        EvaluationRule specializedRule = mock(EvaluationRule.class);
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            1L, 2027, EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of(rule, specializedRule));
        when(rule.getAdmissionType()).thenReturn("학생부교과");
        when(specializedRule.getId()).thenReturn(12L);
        when(specializedRule.getAdmissionType()).thenReturn("특성화고교졸업자");
        when(specializedRule.getRecruitmentUnit()).thenReturn("전체 모집단위");
        when(evaluationService.verify(eq(specializedRule), org.mockito.ArgumentMatchers.any()))
            .thenReturn(verification);
        when(verification.calculations()).thenReturn(List.of());

        TransferApplicationRow application = new TransferApplicationRow(
            2, 2027, "A-001", "12", "특성화고교졸업자", "21", "컴퓨터공학과", 2027
        );
        List<TranscriptExcelRow> courses = List.of(
            course(3, SubjectCategory.KOREAN, "국어"),
            course(4, SubjectCategory.OTHER, "상업경제")
        );
        ApplicantSchoolInfoRow schoolInfo = new ApplicantSchoolInfoRow(
            2, 2027, "A-001", 2027, "S-001", "직업고등학교", "전문학과",
            "실업고", "특성화고", "전문계고교",
            EducationBackground.DOMESTIC_HIGH_SCHOOL, HighSchoolType.SPECIALIZED
        );

        TranscriptBatchVerificationResult result = service.verify(
            1L, 2027, List.of(application), courses, java.util.Map.of("A-001", schoolInfo)
        );

        assertThat(result.successes()).hasSize(1);
        ArgumentCaptor<VerifyGradeRequest> requestCaptor = ArgumentCaptor.forClass(VerifyGradeRequest.class);
        verify(evaluationService).verify(eq(specializedRule), requestCaptor.capture());
        assertThat(requestCaptor.getValue().courses()).extracting(VerifyGradeRequest.CourseGrade::courseName)
            .containsExactly("국어", "상업경제");
    }

    @Test
    void returnsZeroWithoutCalculationWhenSpecializedGraduateApplicantIsNotProfessionalCategory() {
        TranscriptBatchVerificationService service = new TranscriptBatchVerificationService(
            ruleRepository, evaluationService
        );
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            1L, 2027, EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of(rule));
        when(rule.getAdmissionType()).thenReturn("특성화고교졸업자");
        when(rule.getRecruitmentUnit()).thenReturn("전체 모집단위");
        when(rule.getScoreMultiplier()).thenReturn(BigDecimal.TEN);

        TransferApplicationRow application = new TransferApplicationRow(
            2, 2027, "A-001", "12", "특성화고교졸업자", "21", "컴퓨터공학과", 2027
        );
        ApplicantSchoolInfoRow schoolInfo = new ApplicantSchoolInfoRow(
            2, 2027, "A-001", 2027, "S-001", "직업고등학교", "전문학과",
            "실업고", "특성화고", "일반계고교",
            EducationBackground.DOMESTIC_HIGH_SCHOOL, HighSchoolType.SPECIALIZED
        );

        TranscriptBatchVerificationResult result = service.verify(
            1L, 2027, List.of(application),
            List.of(course(3, SubjectCategory.KOREAN, "국어")),
            java.util.Map.of("A-001", schoolInfo)
        );

        assertThat(result.failures()).isEmpty();
        assertThat(result.successes()).singleElement().satisfies(success -> {
            assertThat(success.verification().finalScore()).isZero();
            assertThat(success.verification().baseScore()).isZero();
            assertThat(success.verification().averageGrade()).isNull();
            assertThat(success.verification().includedCourseCount()).isZero();
            assertThat(success.selectedCourses()).isEmpty();
            assertThat(success.verification().warnings())
                .anyMatch(warning -> warning.contains("전문계고교가 아니므로 0점 처리"));
        });
        verifyNoInteractions(evaluationService);
    }

    @Test
    void requiresSchoolInformationForSpecializedGraduateTrack() {
        TranscriptBatchVerificationService service = new TranscriptBatchVerificationService(
            ruleRepository, evaluationService
        );
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            1L, 2027, EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of(rule));
        when(rule.getAdmissionType()).thenReturn("특성화고교졸업자");
        when(rule.getRecruitmentUnit()).thenReturn("전체 모집단위");
        TransferApplicationRow application = new TransferApplicationRow(
            2, 2027, "A-001", "12", "특성화고교졸업자", "21", "컴퓨터공학과", 2027
        );

        TranscriptBatchVerificationResult result = service.verify(
            1L, 2027, List.of(application), List.of(course(3, SubjectCategory.KOREAN, "국어"))
        );

        assertThat(result.successes()).isEmpty();
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.code()).isEqualTo("SCHOOL_INFO_REQUIRED");
            assertThat(failure.reason()).contains("지원자 추가정보 파일");
        });
        verifyNoInteractions(evaluationService);
    }

    @Test
    void describesPhysicalEducationComponentsForRequestedAdmissionYear() {
        TranscriptBatchVerificationService service = new TranscriptBatchVerificationService(
            ruleRepository, evaluationService
        );
        when(ruleRepository.findAllByUniversityIdAndAdmissionYearAndStatus(
            1L, 2026, EvaluationRuleStatus.PUBLISHED
        )).thenReturn(List.of(rule));
        when(rule.getAdmissionType()).thenReturn("체육실기");
        when(rule.getRecruitmentUnit()).thenReturn("전체 모집단위");
        when(evaluationService.verify(eq(rule), org.mockito.ArgumentMatchers.any()))
            .thenReturn(verification);
        when(verification.calculations()).thenReturn(List.of());
        TransferApplicationRow application = new TransferApplicationRow(
            2, 2026, "A-001", "13", "체육실기", "21", "특수체육학과", 2026
        );

        TranscriptBatchVerificationResult result = service.verify(
            1L, 2026, List.of(application), List.of(course(3, SubjectCategory.KOREAN, "국어"))
        );

        assertThat(result.successes()).singleElement().satisfies(success ->
            assertThat(success.verification().warnings())
                .contains("학생부교과 600점만 산출했습니다. 체육실기 400점은 전달양식에 없어 포함하지 않았습니다.")
        );
    }

    private TranscriptExcelRow course(int rowNumber, SubjectCategory category, String name) {
        return new TranscriptExcelRow(
            rowNumber, "A-001", "미등록", null, null, 2027,
            1, 1, category, name, 2, GradeScale.NINE_LEVEL, null,
            null, null, null, null, null, null, null,
            new BigDecimal("3"), false, false
        );
    }

    private TranscriptExcelRow ungradedCourse(int rowNumber, String name) {
        return new TranscriptExcelRow(
            rowNumber, "A-001", "미등록", null, null, 2027,
            1, 1, SubjectCategory.OTHER, name, null, GradeScale.NINE_LEVEL, null,
            null, null, null, null, null, null, null,
            BigDecimal.ONE, false, false
        );
    }

    private GradeVerificationResponse.CourseCalculation calculation(
        String courseName,
        int schoolYear,
        int semester,
        SubjectCategory category,
        String grade,
        String credits,
        boolean included
    ) {
        BigDecimal effectiveGrade = new BigDecimal(grade);
        BigDecimal appliedCredits = new BigDecimal(credits);
        BigDecimal convertedScore = new BigDecimal("100").subtract(effectiveGrade);
        return new GradeVerificationResponse.CourseCalculation(
            courseName, schoolYear, semester, category, category,
            effectiveGrade.intValue(), GradeScale.NINE_LEVEL, null,
            null, null, null, null, null, effectiveGrade, convertedScore,
            BigDecimal.ONE, BigDecimal.ONE, appliedCredits, appliedCredits, appliedCredits,
            included ? convertedScore.multiply(appliedCredits) : BigDecimal.ZERO,
            included, included ? null : "모집요강의 과목 선택 기준에 따라 제외되었습니다."
        );
    }
}
