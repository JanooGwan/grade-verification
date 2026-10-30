package com.jinhakapply.gradevalidation.operation.controller;

import com.jinhakapply.gradevalidation.operation.dto.OperationsDashboardResponse;
import com.jinhakapply.gradevalidation.operation.dto.LightDataStatus;
import com.jinhakapply.gradevalidation.operation.repository.LightDataStatusRepository;
import com.jinhakapply.gradevalidation.operation.dto.ActiveJobsResponse;
import com.jinhakapply.gradevalidation.operation.service.ActiveJobsService;
import com.jinhakapply.gradevalidation.operation.dto.RuleChangeEntry;
import com.jinhakapply.gradevalidation.operation.service.RuleChangeHistoryService;
import java.util.List;
import com.jinhakapply.gradevalidation.operation.service.OperationsDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OperationsController implements OperationsApi {
    private final OperationsDashboardService dashboardService;
    private final LightDataStatusRepository lightDataStatusRepository;
    private final ActiveJobsService activeJobsService;
    private final RuleChangeHistoryService ruleChangeHistoryService;

    @Override
    public ResponseEntity<List<RuleChangeEntry>> getRuleChangeHistory() {
        return ResponseEntity.ok(ruleChangeHistoryService.findAll());
    }

    @Override
    public ResponseEntity<ActiveJobsResponse> getActiveJobs() {
        return ResponseEntity.ok(activeJobsService.findActive());
    }

    @Override
    public ResponseEntity<List<LightDataStatus>> getDataStatus() {
        return ResponseEntity.ok(lightDataStatusRepository.findAll());
    }

    @Override
    public ResponseEntity<OperationsDashboardResponse> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard());
    }
}
