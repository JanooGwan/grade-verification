package com.jinhakapply.gradevalidation.evaluation.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.DUPLICATE_RULE_EXTRACTION_FILE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_RULE_EXTRACTION_FILE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_RULE_EXTRACTION_STATUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleExtraction;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleExtractionEvidenceRepository;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleExtractionRepository;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RuleExtractionServiceTest {

    @Mock PdfRuleHeuristicExtractor extractor;
    @Mock EvaluationRuleExtractionRepository extractionRepository;
    @Mock EvaluationRuleExtractionEvidenceRepository evidenceRepository;
    @Mock EvaluationRuleRepository ruleRepository;
    @Mock UniversityRepository universityRepository;
    @Mock EvaluationService evaluationService;
    @InjectMocks RuleExtractionService service;

    @Test
    void filtersExtractionHistoryByUniversityOnly() {
        EvaluationRuleExtraction extraction = extraction(11L);
        when(extractionRepository.findTop100ByUniversityIdOrderByCreatedAtDesc(1L))
            .thenReturn(List.of(extraction));

        var response = service.findAll(1L, null);

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.extractionId()).isEqualTo(11L);
            assertThat(item.universityId()).isEqualTo(1L);
            assertThat(item.admissionYear()).isEqualTo(2027);
        });
        verify(extractionRepository, never()).findTop100ByOrderByCreatedAtDesc();
    }

    @Test
    void filtersExtractionHistoryByAdmissionYearOnly() {
        when(extractionRepository.findTop100ByAdmissionYearOrderByCreatedAtDesc(2027))
            .thenReturn(List.of(extraction(12L)));

        assertThat(service.findAll(null, 2027)).extracting(item -> item.extractionId())
            .containsExactly(12L);
    }

    @Test
    void usesCombinedFilterWhenBothValuesAreProvided() {
        when(extractionRepository.findTop100ByUniversityIdAndAdmissionYearOrderByCreatedAtDesc(1L, 2027))
            .thenReturn(List.of(extraction(13L)));

        assertThat(service.findAll(1L, 2027)).extracting(item -> item.extractionId())
            .containsExactly(13L);
    }

    @Test
    void rejectsFileWithoutPdfSignatureBeforeExtraction() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "guideline.pdf", "application/pdf", "not-pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(universityRepository.findById(1L)).thenReturn(Optional.of(university()));

        assertThatThrownBy(() -> service.extract(1L, 2027, file))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(INVALID_RULE_EXTRACTION_FILE));
        verifyNoInteractions(extractor);
    }

    @Test
    void rejectsDuplicatePdfBeforeRunningHeuristicExtraction() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "guideline.pdf", "application/pdf", "%PDF-test".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(universityRepository.findById(1L)).thenReturn(Optional.of(university()));
        when(extractionRepository.findFirstByUniversityIdAndAdmissionYearAndFileSha256(
            org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2027), anyString()
        )).thenReturn(Optional.of(extraction(21L)));

        assertThatThrownBy(() -> service.extract(1L, 2027, file))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(DUPLICATE_RULE_EXTRACTION_FILE));
        verifyNoInteractions(extractor);
    }

    @Test
    void preventsCreatingSecondDraftFromSameExtraction() {
        EvaluationRuleExtraction extraction = extraction(31L);
        extraction.attachDraftRule(99L);
        when(extractionRepository.findOneByIdForUpdate(31L)).thenReturn(Optional.of(extraction));

        assertThatThrownBy(() -> service.createDraft(31L, null))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(INVALID_RULE_EXTRACTION_STATUS));
        verifyNoInteractions(evaluationService);
    }

    private EvaluationRuleExtraction extraction(Long id) {
        EvaluationRuleExtraction extraction = EvaluationRuleExtraction.create(
            university(), 2027, "guideline.pdf", "a".repeat(64), 20, 18,
            SelectionStrategy.ALL_COURSES, null,
            List.of(new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("50")),
            List.of(new BigDecimal("100"), new BigDecimal("95")),
            List.of(), List.of(SubjectCategory.KOREAN, SubjectCategory.MATH),
            false, RoundingMode.HALF_UP, "10-12", new BigDecimal("0.9000"),
            List.of(), List.of()
        );
        ReflectionTestUtils.setField(extraction, "id", id);
        return extraction;
    }

    private University university() {
        University university = University.create("TUK", "한국공학대학교");
        ReflectionTestUtils.setField(university, "id", 1L);
        return university;
    }
}
