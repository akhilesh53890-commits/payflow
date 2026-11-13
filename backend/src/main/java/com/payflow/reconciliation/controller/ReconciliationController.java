package com.payflow.reconciliation.controller;

import com.payflow.common.PageResponse;
import com.payflow.reconciliation.domain.ReconciliationRun;
import com.payflow.reconciliation.service.ReconciliationService;
import com.payflow.reconciliation.service.ReconciliationService.RunDetailDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reconciliation")
@Tag(name = "Reconciliation", description = "Endpoints for financial discrepancy detection, auditing, and reconciliation runs")
@PreAuthorize("hasRole('ADMIN')")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/run")
    @Operation(summary = "Execute automated reconciliation comparing payments, ledger journals, and provider states")
    public ResponseEntity<RunDetailDto> runReconciliation() {
        return ResponseEntity.ok(reconciliationService.executeReconciliation());
    }

    @GetMapping("/runs")
    @Operation(summary = "List paginated historical reconciliation runs")
    public ResponseEntity<PageResponse<ReconciliationRun>> listRuns(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(reconciliationService.listRuns(pageable)));
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "Get reconciliation run summary and discrepancy line items")
    public ResponseEntity<RunDetailDto> getRunDetails(@PathVariable String id) {
        return ResponseEntity.ok(reconciliationService.getRunDetails(id));
    }
}

