package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.VERIFICATION_RUN_NOT_FOUND;

import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
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
}
