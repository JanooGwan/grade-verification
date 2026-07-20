package com.jinhakapply.gradevalidation.evaluation.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_EVALUATION_RULE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_RULE_EXTRACTION_FILE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_RULE_EXTRACTION_STATUS;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.RULE_EXTRACTION_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.UNIVERSITY_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.DUPLICATE_RULE_EXTRACTION_FILE;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleExtraction;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleExtractionEvidence;
import com.jinhakapply.gradevalidation.evaluation.domain.RuleExtractionStatus;
import com.jinhakapply.gradevalidation.evaluation.dto.CreateEvaluationRuleRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.EvaluationRuleResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.RuleExtractionResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.RuleExtractionSummaryResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.RuleExtractionComparisonResponse;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleExtractionEvidenceRepository;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleExtractionRepository;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleExtractionService {
    private static final long MAX_PDF_SIZE = 30L * 1024 * 1024;

    private final PdfRuleHeuristicExtractor extractor;
    private final EvaluationRuleExtractionRepository extractionRepository;
    private final EvaluationRuleExtractionEvidenceRepository evidenceRepository;
    private final EvaluationRuleRepository ruleRepository;
    private final UniversityRepository universityRepository;
    private final EvaluationService evaluationService;

    @Transactional
    public RuleExtractionResponse extract(Long universityId, int admissionYear, MultipartFile file) {
        validateMetadata(universityId, admissionYear);
        String fileName = validateFile(file);
        University university = universityRepository.findById(universityId)
            .orElseThrow(() -> CustomException.of(UNIVERSITY_NOT_FOUND, universityId.toString()));

        try {
            byte[] bytes = file.getBytes();
            if (!hasPdfSignature(bytes)) {
                throw CustomException.of(INVALID_RULE_EXTRACTION_FILE, "PDF 서명이 확인되지 않습니다.");
            }
            String fileHash = sha256(bytes);
            extractionRepository.findFirstByUniversityIdAndAdmissionYearAndFileSha256(
                universityId, admissionYear, fileHash
            ).ifPresent(existing -> {
                throw CustomException.of(DUPLICATE_RULE_EXTRACTION_FILE,
                    "기존 추출 ID: " + existing.getId());
            });
            RuleExtractionAnalysis analysis = extractor.extract(bytes);
            EvaluationRuleExtraction extraction;
            try {
                extraction = extractionRepository.saveAndFlush(EvaluationRuleExtraction.create(
                    university,
                    admissionYear,
                    fileName,
                    fileHash,
                    analysis.pageCount(),
                    analysis.textPageCount(),
                    analysis.selectionStrategy(),
                    analysis.selectionCount(),
                    analysis.gradeWeights(),
                    analysis.gradeScores(),
                    analysis.achievementScores(),
                    analysis.subjectCategories(),
                    analysis.includeThirdYearSecondSemester(),
                    analysis.roundingMode(),
                    analysis.sourcePages(),
                    analysis.overallConfidence(),
                    analysis.missingFields(),
                    analysis.warnings()
                ));
            } catch (DataIntegrityViolationException exception) {
                throw CustomException.of(DUPLICATE_RULE_EXTRACTION_FILE);
            }
            List<EvaluationRuleExtractionEvidence> evidence = analysis.evidence().stream()
                .map(item -> EvaluationRuleExtractionEvidence.create(
                    extraction, item.fieldKey(), item.pageNumber(), item.excerpt(), item.confidence()))
                .toList();
            return RuleExtractionResponse.of(extraction, evidenceRepository.saveAll(evidence));
        } catch (IOException exception) {
            throw CustomException.of(INVALID_RULE_EXTRACTION_FILE, exception.getMessage());
        }
    }

    public RuleExtractionResponse find(Long extractionId) {
        EvaluationRuleExtraction extraction = findExtraction(extractionId);
        return RuleExtractionResponse.of(extraction,
            evidenceRepository.findAllByExtraction_IdOrderByPageNumberAscFieldKeyAsc(extractionId));
    }

    public List<RuleExtractionSummaryResponse> findAll(Long universityId, Integer admissionYear) {
        List<EvaluationRuleExtraction> extractions;
        if (universityId != null && admissionYear != null) {
            extractions = extractionRepository.findTop100ByUniversityIdAndAdmissionYearOrderByCreatedAtDesc(
                universityId, admissionYear
            );
        } else if (universityId != null) {
            extractions = extractionRepository.findTop100ByUniversityIdOrderByCreatedAtDesc(universityId);
        } else if (admissionYear != null) {
            extractions = extractionRepository.findTop100ByAdmissionYearOrderByCreatedAtDesc(admissionYear);
        } else {
            extractions = extractionRepository.findTop100ByOrderByCreatedAtDesc();
        }
        return extractions.stream().map(RuleExtractionSummaryResponse::from).toList();
    }

    public RuleExtractionComparisonResponse compare(Long leftId, Long rightId) {
        RuleExtractionResponse left = find(leftId);
        RuleExtractionResponse right = find(rightId);
        java.util.ArrayList<RuleExtractionComparisonResponse.FieldDifference> differences = new java.util.ArrayList<>();
        addDifference(differences, "선택 방식", left.candidate().selectionStrategy(), right.candidate().selectionStrategy());
        addDifference(differences, "선택 개수", left.candidate().selectionCount(), right.candidate().selectionCount());
        addDifference(differences, "학년 비율", left.candidate().gradeWeights(), right.candidate().gradeWeights());
        addDifference(differences, "등급 환산표", left.candidate().gradeScores(), right.candidate().gradeScores());
        addDifference(differences, "성취도 환산표", left.candidate().achievementScores(), right.candidate().achievementScores());
        addDifference(differences, "반영 교과", left.candidate().subjectCategories(), right.candidate().subjectCategories());
        addDifference(differences, "3학년 2학기", left.candidate().includeThirdYearSecondSemester(), right.candidate().includeThirdYearSecondSemester());
        addDifference(differences, "반올림", left.candidate().roundingMode(), right.candidate().roundingMode());
        addDifference(differences, "근거 페이지", left.candidate().sourcePages(), right.candidate().sourcePages());
        return new RuleExtractionComparisonResponse(left, right, List.copyOf(differences));
    }

    private void addDifference(
        List<RuleExtractionComparisonResponse.FieldDifference> differences,
        String field,
        Object left,
        Object right
    ) {
        if (!java.util.Objects.equals(left, right)) {
            differences.add(new RuleExtractionComparisonResponse.FieldDifference(
                field, String.valueOf(left), String.valueOf(right)
            ));
        }
    }

    @Transactional
    public EvaluationRuleResponse createDraft(Long extractionId, CreateEvaluationRuleRequest request) {
        EvaluationRuleExtraction extraction = extractionRepository.findOneByIdForUpdate(extractionId)
            .orElseThrow(() -> CustomException.of(RULE_EXTRACTION_NOT_FOUND, extractionId.toString()));
        if (extraction.getStatus() != RuleExtractionStatus.EXTRACTED || extraction.getDraftRuleId() != null) {
            throw CustomException.of(INVALID_RULE_EXTRACTION_STATUS, "이미 초안이 생성된 추출 결과입니다.");
        }
        if (!extraction.getUniversity().getId().equals(request.universityId())
            || extraction.getAdmissionYear() != request.admissionYear()) {
            throw CustomException.of(INVALID_EVALUATION_RULE, "추출 대상 대학과 모집연도가 초안 정보와 다릅니다.");
        }
        if (!StringUtils.hasText(request.sourceDocument()) || !StringUtils.hasText(request.sourcePages())) {
            throw CustomException.of(INVALID_EVALUATION_RULE, "추출 근거 문서와 페이지를 입력해 주세요.");
        }

        EvaluationRuleResponse created = evaluationService.createRule(request);
        EvaluationRule rule = ruleRepository.findOneById(created.id())
            .orElseThrow(() -> CustomException.of(INVALID_EVALUATION_RULE));
        rule.attachExtraction(extractionId);
        extraction.attachDraftRule(rule.getId());
        return EvaluationRuleResponse.from(rule);
    }

    private EvaluationRuleExtraction findExtraction(Long extractionId) {
        return extractionRepository.findOneById(extractionId)
            .orElseThrow(() -> CustomException.of(RULE_EXTRACTION_NOT_FOUND, extractionId.toString()));
    }

    private void validateMetadata(Long universityId, int admissionYear) {
        if (universityId == null || universityId <= 0 || admissionYear < 2000 || admissionYear > 2100) {
            throw CustomException.of(INVALID_RULE_EXTRACTION_FILE, "대학과 모집연도를 확인해 주세요.");
        }
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_PDF_SIZE) {
            throw CustomException.of(INVALID_RULE_EXTRACTION_FILE, "30MB 이하의 PDF를 업로드해 주세요.");
        }
        String fileName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "guideline.pdf" : file.getOriginalFilename());
        if (fileName.contains("..") || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            throw CustomException.of(INVALID_RULE_EXTRACTION_FILE, "PDF 확장자만 허용됩니다.");
        }
        return fileName;
    }

    private boolean hasPdfSignature(byte[] bytes) {
        return bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
            && bytes[3] == 'F' && bytes[4] == '-';
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
