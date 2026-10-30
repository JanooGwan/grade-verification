package com.jinhakapply.gradevalidation.operation.controller;

import com.jinhakapply.gradevalidation.operation.dto.OperationsDashboardResponse;
import com.jinhakapply.gradevalidation.operation.dto.LightDataStatus;
import com.jinhakapply.gradevalidation.operation.dto.ActiveJobsResponse;
import com.jinhakapply.gradevalidation.operation.dto.RuleChangeEntry;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Operations", description = "운영 현황 및 애플리케이션 요청 지표 API")
@RequestMapping("/api/operations")
public interface OperationsApi {
    @Operation(summary = "코드에 기록된 성적 로직 변경 이력 조회")
    @GetMapping("/rule-change-history")
    ResponseEntity<List<RuleChangeEntry>> getRuleChangeHistory();
    @Operation(summary = "진행 중인 성적 작업 조회")
    @GetMapping("/active-jobs")
    ResponseEntity<ActiveJobsResponse> getActiveJobs();
    @Operation(summary = "대학·연도별 저장 여부와 최근 일시 조회")
    @GetMapping("/data-status")
    ResponseEntity<List<LightDataStatus>> getDataStatus();
    @Operation(summary = "운영 대시보드 조회")
    @GetMapping("/dashboard")
    ResponseEntity<OperationsDashboardResponse> getDashboard();
}
