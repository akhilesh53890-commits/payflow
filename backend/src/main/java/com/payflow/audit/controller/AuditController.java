package com.payflow.audit.controller;

import com.payflow.audit.domain.AuditLog;
import com.payflow.audit.service.AuditService;
import com.payflow.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit Trail", description = "Append-oriented immutable security and financial audit trail (Admin only)")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/logs")
    @Operation(summary = "List paginated audit records")
    public ResponseEntity<PageResponse<AuditLog>> listLogs(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(auditService.listLogs(pageable)));
    }
}

