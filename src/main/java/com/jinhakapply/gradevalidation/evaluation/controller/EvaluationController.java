package com.jinhakapply.gradevalidation.evaluation.controller;

import java.net.URI;
import java.util.List;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.dto.BulkCreateEvaluationRuleRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.CreateEvaluationRuleRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.ConfigureSelectionPolicyRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.EvaluationRuleActionRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.EvaluationRuleResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.VerifyGradeRequest;
import com.jinhakapply.gradevalidation.evaluation.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EvaluationController implements EvaluationApi {
    private final EvaluationService evaluationService;
    @Override public ResponseEntity<EvaluationRuleResponse> createRule(CreateEvaluationRuleRequest request) {
        EvaluationRuleResponse response = evaluationService.createRule(request);
        return ResponseEntity.created(URI.create("/api/evaluations/rules/" + response.id())).body(response);
    }
    @Override
    public ResponseEntity<List<EvaluationRuleResponse>> findRules() {
        return ResponseEntity.ok(evaluationService.findRules());
    }

    @Override
    public ResponseEntity<List<EvaluationRuleResponse>> createDraftRules(BulkCreateEvaluationRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(evaluationService.createDraftRules(request));
    }

    @Override
    public ResponseEntity<List<EvaluationRuleResponse>> findAdminRules(EvaluationRuleStatus status) {
        return ResponseEntity.ok(evaluationService.findAdminRules(status));
    }

    @Override
    public ResponseEntity<EvaluationRuleResponse> reviewRule(
        Long ruleId,
        EvaluationRuleActionRequest request
    ) {
        return ResponseEntity.ok(evaluationService.reviewRule(ruleId, request));
    }

    @Override
    public ResponseEntity<EvaluationRuleResponse> publishRule(
        Long ruleId,
        EvaluationRuleActionRequest request
    ) {
        return ResponseEntity.ok(evaluationService.publishRule(ruleId, request));
    }

    @Override
    public ResponseEntity<EvaluationRuleResponse> retireRule(
        Long ruleId,
        EvaluationRuleActionRequest request
    ) {
        return ResponseEntity.ok(evaluationService.retireRule(ruleId, request));
    }

    @Override
    public ResponseEntity<EvaluationRuleResponse> configureSelectionPolicy(
        Long ruleId,
        ConfigureSelectionPolicyRequest request
    ) {
        return ResponseEntity.ok(evaluationService.configureSelectionPolicy(ruleId, request));
    }

    @Override
    public ResponseEntity<GradeVerificationResponse> verify(VerifyGradeRequest request) {
        return ResponseEntity.ok(evaluationService.verify(request));
    }
}
