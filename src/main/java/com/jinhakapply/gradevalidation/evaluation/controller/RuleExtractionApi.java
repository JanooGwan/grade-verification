package com.jinhakapply.gradevalidation.evaluation.controller;

import com.jinhakapply.gradevalidation.evaluation.dto.CreateEvaluationRuleRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.EvaluationRuleResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.RuleExtractionResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.RuleExtractionSummaryResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.RuleExtractionComparisonResponse;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Validated
@Tag(name = "Rule extraction", description = "모집요강 PDF에서 근거가 확인된 성적 반영 규칙 후보를 추출하는 API")
@RequestMapping("/api/evaluations/rule-extractions")
public interface RuleExtractionApi {

    @Operation(summary = "모집요강 PDF 규칙 후보 추출",
        description = "후보와 필드별 근거·신뢰도·경고를 반환하며 규칙을 자동 게시하지 않습니다.")
    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<RuleExtractionResponse> extract(
        @RequestParam("universityId") @Positive Long universityId,
        @RequestParam("admissionYear") @Min(2000) @Max(2100) int admissionYear,
        @RequestPart("file") MultipartFile file
    );

    @Operation(summary = "규칙 추출 결과 조회")
    @GetMapping("/{extractionId}")
    ResponseEntity<RuleExtractionResponse> find(@PathVariable @Positive Long extractionId);

    @Operation(summary = "모집요강 추출 문서 이력 조회")
    @GetMapping
    ResponseEntity<List<RuleExtractionSummaryResponse>> findAll(
        @RequestParam(required = false) Long universityId,
        @RequestParam(required = false) Integer admissionYear
    );

    @Operation(summary = "두 모집요강 추출 결과 비교")
    @GetMapping("/compare")
    ResponseEntity<RuleExtractionComparisonResponse> compare(
        @RequestParam @Positive Long leftId,
        @RequestParam @Positive Long rightId
    );

    @Operation(summary = "검토한 추출 결과를 규칙 초안으로 저장",
        description = "사용자가 수정·확정한 값으로 DRAFT 규칙을 생성합니다. 검증 및 게시 단계는 별도로 수행합니다.")
    @PostMapping("/{extractionId}/draft")
    ResponseEntity<EvaluationRuleResponse> createDraft(
        @PathVariable @Positive Long extractionId,
        @Valid @RequestBody CreateEvaluationRuleRequest request
    );
}
