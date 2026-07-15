package com.jinhakapply.gradevalidation.operation.controller;

import com.jinhakapply.gradevalidation.operation.dto.OperationsDashboardResponse;
import com.jinhakapply.gradevalidation.operation.service.OperationsDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OperationsController implements OperationsApi {
    private final OperationsDashboardService dashboardService;

    @Override
    public ResponseEntity<OperationsDashboardResponse> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard());
    }
}
