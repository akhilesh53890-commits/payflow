package com.payflow.risk.controller;

import com.payflow.common.PageResponse;
import com.payflow.common.exception.ForbiddenException;
import com.payflow.identity.security.PayflowUserPrincipal;
import com.payflow.risk.domain.RiskAssessment;
import com.payflow.risk.repository.RiskAssessmentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/risk")
@Tag(name = "Risk Engine", description = "Endpoints for viewing explainable risk assessments and decision rules")
public class RiskController {

    private final RiskAssessmentRepository riskAssessmentRepository;

    public RiskController(RiskAssessmentRepository riskAssessmentRepository) {
        this.riskAssessmentRepository = riskAssessmentRepository;
    }

    @GetMapping("/assessments")
    @Operation(summary = "List risk assessments with scores and explainable rule reasons")
    public ResponseEntity<PageResponse<RiskAssessment>> listAssessments(
            @AuthenticationPrincipal PayflowUserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        String merchantId = "ADMIN".equals(principal.getRole()) ? null : principal.getMerchantId();
        if (merchantId != null) {
            return ResponseEntity.ok(PageResponse.from(
                    riskAssessmentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable)));
        } else {
            return ResponseEntity.ok(PageResponse.from(riskAssessmentRepository.findAll(pageable)));
        }
    }
}

