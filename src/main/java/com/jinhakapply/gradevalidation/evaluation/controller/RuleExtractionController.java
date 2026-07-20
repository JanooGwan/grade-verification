package com.jinhakapply.gradevalidation.evaluation.controller;

import java.net.URI;

import com.jinhakapply.gradevalidation.evaluation.dto.CreateEvaluationRuleRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.EvaluationRuleResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.RuleExtractionResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.RuleExtractionSummaryResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.RuleExtractionComparisonResponse;
import java.util.List;
import com.jinhakapply.gradevalidation.evaluation.service.RuleExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class RuleExtractionController implements RuleExtractionApi {
    private final RuleExtractionService ruleExtractionService;

    @Override
    public ResponseEntity<RuleExtractionResponse> extract(
        Long universityId,
        int admissionYear,
        MultipartFile file
    ) {
        RuleExtractionResponse response = ruleExtractionService.extract(universityId, admissionYear, file);
        return ResponseEntity.created(URI.create(
            "/api/evaluations/rule-extractions/" + response.extractionId())).body(response);
    }

    @Override
    public ResponseEntity<RuleExtractionResponse> find(Long extractionId) {
        return ResponseEntity.ok(ruleExtractionService.find(extractionId));
    }

    @Override
    public ResponseEntity<List<RuleExtractionSummaryResponse>> findAll(Long universityId, Integer admissionYear) {
        return ResponseEntity.ok(ruleExtractionService.findAll(universityId, admissionYear));
    }

    @Override
    public ResponseEntity<RuleExtractionComparisonResponse> compare(Long leftId, Long rightId) {
        return ResponseEntity.ok(ruleExtractionService.compare(leftId, rightId));
    }

    @Override
    public ResponseEntity<EvaluationRuleResponse> createDraft(
        Long extractionId,
        CreateEvaluationRuleRequest request
    ) {
        EvaluationRuleResponse response = ruleExtractionService.createDraft(extractionId, request);
        return ResponseEntity.created(URI.create("/api/evaluations/rules/" + response.id())).body(response);
    }
}
