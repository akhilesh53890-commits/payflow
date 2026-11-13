package com.payflow.refund.controller;

import com.payflow.common.PageResponse;
import com.payflow.common.exception.ForbiddenException;
import com.payflow.identity.security.PayflowUserPrincipal;
import com.payflow.refund.dto.RefundDtos.CreateRefundRequest;
import com.payflow.refund.dto.RefundDtos.RefundDto;
import com.payflow.refund.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/refunds")
@Tag(name = "Refunds", description = "Endpoints for full and partial refunds with concurrent refund protection and double-entry ledger reversals")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    @Operation(summary = "Process a full or partial payment refund")
    public ResponseEntity<RefundDto> createRefund(
            @AuthenticationPrincipal PayflowUserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request,
            HttpServletRequest servletRequest
    ) {
        String merchantId = getRequiredMerchantId(principal);
        String ipAddress = servletRequest.getRemoteAddr();

        RefundDto dto = refundService.processRefund(merchantId, idempotencyKey, request, principal.getUsername(), ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    @Operation(summary = "List paginated refunds")
    public ResponseEntity<PageResponse<RefundDto>> listRefunds(
            @AuthenticationPrincipal PayflowUserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        String merchantId = "ADMIN".equals(principal.getRole()) ? null : getRequiredMerchantId(principal);
        return ResponseEntity.ok(PageResponse.from(refundService.listMerchantRefunds(merchantId, pageable)));
    }

    @GetMapping("/payment/{paymentId}")
    @Operation(summary = "List all refunds for a specific payment")
    public ResponseEntity<List<RefundDto>> listPaymentRefunds(@PathVariable String paymentId) {
        return ResponseEntity.ok(refundService.listPaymentRefunds(paymentId));
    }

    private String getRequiredMerchantId(PayflowUserPrincipal principal) {
        if (principal == null || principal.getMerchantId() == null) {
            throw new ForbiddenException("Operation requires an authenticated merchant context.");
        }
        return principal.getMerchantId();
    }
}

