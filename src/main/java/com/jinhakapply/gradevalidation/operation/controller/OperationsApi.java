package com.jinhakapply.gradevalidation.operation.controller;

import com.jinhakapply.gradevalidation.operation.dto.OperationsDashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Operations", description = "운영 현황 및 애플리케이션 요청 지표 API")
@RequestMapping("/api/operations")
public interface OperationsApi {
    @Operation(summary = "운영 대시보드 조회")
    @GetMapping("/dashboard")
    ResponseEntity<OperationsDashboardResponse> getDashboard();
}
