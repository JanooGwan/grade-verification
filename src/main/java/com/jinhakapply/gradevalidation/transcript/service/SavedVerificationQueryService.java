package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.VERIFICATION_RUN_NOT_FOUND;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
    private static final int EXPORT_PAGE_SIZE = 200;
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
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeExport(sourceImportId, output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("저장 검증 결과 Excel 출력 스트림을 닫지 못했습니다.", exception);
        }
    }

    @Transactional(readOnly = true)
    public void writeExport(Long sourceImportId, OutputStream output) throws IOException {
        SavedVerificationBatchResponse batch = repository.findBatch(sourceImportId)
            .orElseThrow(() -> CustomException.of(VERIFICATION_RUN_NOT_FOUND));
        if (SyuSourceExcelStreamer.SOURCE_FORMAT.equals(batch.sourceFormat())) {
            output.write(syuSavedVerificationExcelWriter.write(batch));
            return;
        }

        List<TranscriptBatchVerificationResult.Success> successes = new ArrayList<>();
        List<TranscriptExcelRow> courses = new ArrayList<>();
        Map<Long, EvaluationRule> rules = new HashMap<>();
        int courseRowNumber = 1;
        long afterVerificationRunId = 0L;
        int resultIndex = 0;
        while (true) {
            List<SavedVerificationQueryRepository.ExportProjection> storedResults =
                repository.findExportResultsAfter(sourceImportId, afterVerificationRunId, EXPORT_PAGE_SIZE);
            if (storedResults.isEmpty()) break;
            for (SavedVerificationQueryRepository.ExportProjection stored : storedResults) {
                GradeVerificationResponse verification = objectMapper.readValue(
                    stored.resultJson(), GradeVerificationResponse.class
                );
                EvaluationRule rule = rules.computeIfAbsent(stored.ruleId(), ruleId ->
                    ruleRepository.findOneById(ruleId)
                        .orElseThrow(() -> CustomException.of(VERIFICATION_RUN_NOT_FOUND))
                );
                TransferApplicationRow application = new TransferApplicationRow(
                    stored.applicationId(), ++resultIndex, batch.admissionYear(), stored.applicantNumber(),
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
                    withoutCalculations(verification),
                    List.copyOf(selectedCourses),
                    batchVerificationService.buildIntermediateCalculations(rule, verification),
                    null
                ));
                afterVerificationRunId = stored.verificationRunId();
            }
            if (storedResults.size() < EXPORT_PAGE_SIZE) break;
        }
        if (successes.isEmpty()) throw CustomException.of(VERIFICATION_RUN_NOT_FOUND);

        TranscriptBatchVerificationResult result = new TranscriptBatchVerificationResult(
            List.copyOf(successes), List.of()
        );
        validationExcelWriter.write(
            batch.originalFileName(), batch.sourceFormat(), batch.universityName(),
            successes.size(), courses.size(), List.of(), List.copyOf(courses), List.of(),
            List.of("DB에 저장된 검증 회차 #%d 결과를 재계산 없이 내보냈습니다.".formatted(sourceImportId)),
            result, output
        );
    }

    private GradeVerificationResponse withoutCalculations(GradeVerificationResponse verification) {
        return new GradeVerificationResponse(
            verification.ruleId(), verification.ruleName(), verification.ruleVersion(),
            verification.universityName(), verification.admissionType(), verification.recruitmentUnit(),
            verification.finalScore(), verification.baseScore(), verification.averageGrade(),
            verification.selectionStrategy(), verification.scoreAggregation(), verification.sourceDocument(),
            verification.sourcePages(), verification.includedCourseCount(), verification.excludedCourseCount(),
            verification.calculationSummary(), List.of(), verification.warnings()
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
