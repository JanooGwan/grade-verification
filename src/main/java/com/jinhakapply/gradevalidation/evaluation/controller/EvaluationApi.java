package com.jinhakapply.gradevalidation.evaluation.controller;

import java.util.List;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.dto.BulkCreateEvaluationRuleRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.CreateEvaluationRuleRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.EvaluationRuleActionRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.EvaluationRuleResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.VerifyGradeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Grade evaluation", description = "내신 반영 규칙 및 성적 검증 API")
@RequestMapping("/api/evaluations")
public interface EvaluationApi {
    @Operation(summary = "대학별 성적 반영 규칙 등록")
    @PostMapping("/rules")
    ResponseEntity<EvaluationRuleResponse> createRule(@Valid @RequestBody CreateEvaluationRuleRequest request);
    @Operation(summary = "성적 반영 규칙 목록 조회")
    @GetMapping("/rules")
    ResponseEntity<List<EvaluationRuleResponse>> findRules();

    @Operation(summary = "성적 반영 규칙 초안 일괄 등록")
    @PostMapping("/rules/drafts/bulk")
    ResponseEntity<List<EvaluationRuleResponse>> createDraftRules(
        @Valid @RequestBody BulkCreateEvaluationRuleRequest request
    );

    @Operation(summary = "관리자용 성적 반영 규칙 목록 조회")
    @GetMapping("/rules/admin")
    ResponseEntity<List<EvaluationRuleResponse>> findAdminRules(
        @RequestParam(required = false) EvaluationRuleStatus status
    );

    @Operation(summary = "성적 반영 규칙 검수 완료")
    @PatchMapping("/rules/{ruleId}/review")
    ResponseEntity<EvaluationRuleResponse> reviewRule(
        @PathVariable Long ruleId,
        @Valid @RequestBody EvaluationRuleActionRequest request
    );

    @Operation(summary = "검수된 성적 반영 규칙 게시")
    @PatchMapping("/rules/{ruleId}/publish")
    ResponseEntity<EvaluationRuleResponse> publishRule(
        @PathVariable Long ruleId,
        @Valid @RequestBody EvaluationRuleActionRequest request
    );

    @Operation(summary = "성적 반영 규칙 폐기")
    @PatchMapping("/rules/{ruleId}/retire")
    ResponseEntity<EvaluationRuleResponse> retireRule(
        @PathVariable Long ruleId,
        @Valid @RequestBody EvaluationRuleActionRequest request
    );
    @Operation(summary = "학생 내신 성적 검증")
    @PostMapping("/verify")
    ResponseEntity<GradeVerificationResponse> verify(@Valid @RequestBody VerifyGradeRequest request);
}
