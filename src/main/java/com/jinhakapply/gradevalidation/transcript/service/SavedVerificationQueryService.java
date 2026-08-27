package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.VERIFICATION_RUN_NOT_FOUND;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationBatchResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationDetailResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationPageResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationResultRow;
import com.jinhakapply.gradevalidation.transcript.repository.SavedVerificationQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SavedVerificationQueryService {
    private final SavedVerificationQueryRepository repository;
    private final ObjectMapper objectMapper;
    private final EvaluationRuleRepository ruleRepository;
    private final TranscriptBatchVerificationService batchVerificationService;
    private final TranscriptValidationExcelWriter validationExcelWriter;
    private final SyuSavedVerificationExcelWriter syuSavedVerificationExcelWriter;

    @Transactional(readOnly = true)
    public List<SavedVerificationBatchResponse> findBatches(Long universityId, int admissionYear) {
        return repository.findBatches(universityId, admissionYear);
    }

    @Transactional(readOnly = true)
    public SavedVerificationPageResponse findResults(Long sourceImportId, String keyword, int page, int size) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        long totalElements = repository.countResults(sourceImportId, normalizedKeyword);
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        List<SavedVerificationResultRow> content = repository.findResults(
            sourceImportId, normalizedKeyword, size, (long) page * size
        );
        return new SavedVerificationPageResponse(
            content,
            page,
            size,
            totalElements,
            totalPages,
            page == 0,
            totalPages == 0 || page >= totalPages - 1
        );
    }

    @Transactional(readOnly = true)
    public SavedVerificationDetailResponse findDetail(Long verificationRunId) {
        SavedVerificationQueryRepository.DetailProjection result = repository.findDetail(verificationRunId)
            .orElseThrow(() -> CustomException.of(VERIFICATION_RUN_NOT_FOUND));
        GradeVerificationResponse verification = objectMapper.readValue(
            result.resultJson(), GradeVerificationResponse.class
        );
        return new SavedVerificationDetailResponse(
            result.verificationRunId(),
            result.sourceImportId(),
            result.studentId(),
            result.applicantNumber(),
            result.studentName(),
            result.savedAt(),
            verification
        );
    }

    @Transactional(readOnly = true)
    public byte[] export(Long sourceImportId) {
        SavedVerificationBatchResponse batch = repository.findBatch(sourceImportId)
            .orElseThrow(() -> CustomException.of(VERIFICATION_RUN_NOT_FOUND));
        if (SyuSourceExcelStreamer.SOURCE_FORMAT.equals(batch.sourceFormat())) {
            return syuSavedVerificationExcelWriter.write(batch);
        }
        List<SavedVerificationQueryRepository.ExportProjection> storedResults =
            repository.findExportResults(sourceImportId);
        if (storedResults.isEmpty()) throw CustomException.of(VERIFICATION_RUN_NOT_FOUND);

        List<TranscriptBatchVerificationResult.Success> successes = new ArrayList<>();
        List<TranscriptExcelRow> courses = new ArrayList<>();
        Map<Long, EvaluationRule> rules = new HashMap<>();
        int courseRowNumber = 1;
        for (int resultIndex = 0; resultIndex < storedResults.size(); resultIndex++) {
            SavedVerificationQueryRepository.ExportProjection stored = storedResults.get(resultIndex);
            GradeVerificationResponse verification = objectMapper.readValue(
                stored.resultJson(), GradeVerificationResponse.class
            );
            EvaluationRule rule = rules.computeIfAbsent(stored.ruleId(), ruleId ->
                ruleRepository.findOneById(ruleId)
                    .orElseThrow(() -> CustomException.of(VERIFICATION_RUN_NOT_FOUND))
            );
            TransferApplicationRow application = new TransferApplicationRow(
                stored.applicationId(), resultIndex + 1, batch.admissionYear(), stored.applicantNumber(),
                null, stored.admissionTrackName(), stored.recruitmentUnitCode(),
                stored.recruitmentUnitName(), null, null
            );
            List<TranscriptBatchVerificationResult.SelectedCourse> selectedCourses = new ArrayList<>();
            List<GradeVerificationResponse.CourseCalculation> calculations = verification.calculations() == null
                ? List.of() : verification.calculations();
            for (GradeVerificationResponse.CourseCalculation calculation : calculations) {
                TranscriptExcelRow course = storedCourse(
                    courseRowNumber++, stored.applicantNumber(), stored.studentName(), calculation
                );
                courses.add(course);
                if (calculation.included()) {
                    selectedCourses.add(new TranscriptBatchVerificationResult.SelectedCourse(course, calculation));
                }
            }
            successes.add(new TranscriptBatchVerificationResult.Success(
                application,
                stored.studentName(),
                verification,
                List.copyOf(selectedCourses),
                batchVerificationService.buildKbuIntermediateCalculations(rule, verification),
                null
            ));
        }

        TranscriptBatchVerificationResult result = new TranscriptBatchVerificationResult(
            List.copyOf(successes), List.of()
        );
        return validationExcelWriter.write(
            batch.originalFileName(), batch.sourceFormat(), batch.universityName(),
            successes.size(), courses.size(), List.of(), List.copyOf(courses), List.of(),
            List.of("DB에 저장된 검증 회차 #%d 결과를 재계산 없이 내보냈습니다.".formatted(sourceImportId)),
            result
        );
    }

    private TranscriptExcelRow storedCourse(
        int rowNumber,
        String applicantNumber,
        String studentName,
        GradeVerificationResponse.CourseCalculation calculation
    ) {
        return new TranscriptExcelRow(
            rowNumber, applicantNumber, studentName, null, null, null,
            calculation.schoolYear(), calculation.semester(), calculation.subjectCategory(),
            calculation.courseName(), calculation.grade(), calculation.gradeScale(), calculation.achievement(),
            null, null, null, calculation.cohortSize(), calculation.rankPosition(), calculation.tiedRankCount(),
            calculation.legacyAchievement(), calculation.credits(), false, false
        );
    }
}
