package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.VerifyGradeRequest;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import com.jinhakapply.gradevalidation.transcript.domain.GradeScale;
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
    void verifiesEachApplicationWithPublishedRuleAndHanshinSubjects() {
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
            course(5, SubjectCategory.OTHER, "미술")
        );

        TranscriptBatchVerificationResult result = service.verify(1L, 2027, List.of(application), courses);

        assertThat(result.successes()).hasSize(1);
        assertThat(result.failures()).isEmpty();
        ArgumentCaptor<VerifyGradeRequest> requestCaptor = ArgumentCaptor.forClass(VerifyGradeRequest.class);
        verify(evaluationService).verify(eq(rule), requestCaptor.capture());
        assertThat(requestCaptor.getValue().courses()).extracting(VerifyGradeRequest.CourseGrade::courseName)
            .containsExactly("국어", "한국사");
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

    private TranscriptExcelRow course(int rowNumber, SubjectCategory category, String name) {
        return new TranscriptExcelRow(
            rowNumber, "A-001", "미등록", null, null, 2027,
            1, 1, category, name, 2, GradeScale.NINE_LEVEL, null,
            null, null, null, null, null, null, null,
            new BigDecimal("3"), false, false
        );
    }
}
