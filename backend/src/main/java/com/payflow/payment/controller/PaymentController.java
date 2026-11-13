package com.payflow.payment.controller;

import com.payflow.common.PageResponse;
import com.payflow.common.exception.ForbiddenException;
import com.payflow.identity.security.PayflowUserPrincipal;
import com.payflow.payment.domain.PaymentState;
import com.payflow.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.payflow.payment.dto.PaymentDtos.PaymentDetailDto;
import com.payflow.payment.dto.PaymentDtos.PaymentDto;
import com.payflow.payment.service.PaymentService;
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

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Endpoints for payment creation, lifecycle state machine, idempotency, and retrieval")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Create and process a payment with durable idempotency")
    public ResponseEntity<PaymentDto> createPayment(
            @AuthenticationPrincipal PayflowUserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest servletRequest
    ) {
        String merchantId = getRequiredMerchantId(principal);
        String ipAddress = servletRequest.getRemoteAddr();

        PaymentDto dto = paymentService.processPayment(merchantId, idempotencyKey, request, principal.getUsername(), ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full payment details, provider attempts, and state transition history")
    public ResponseEntity<PaymentDetailDto> getPayment(
            @AuthenticationPrincipal PayflowUserPrincipal principal,
            @PathVariable String id
    ) {
        String merchantId = "ADMIN".equals(principal.getRole()) ? null : getRequiredMerchantId(principal);
        return ResponseEntity.ok(paymentService.getPaymentDetail(id, merchantId));
    }

    @GetMapping
    @Operation(summary = "List paginated payments with optional status filter")
    public ResponseEntity<PageResponse<PaymentDto>> listPayments(
            @AuthenticationPrincipal PayflowUserPrincipal principal,
            @RequestParam(required = false) PaymentState status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        String merchantId = "ADMIN".equals(principal.getRole()) ? null : getRequiredMerchantId(principal);
        return ResponseEntity.ok(PageResponse.from(paymentService.listPayments(merchantId, status, pageable)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a payment in CREATED state")
    public ResponseEntity<PaymentDto> cancelPayment(
            @AuthenticationPrincipal PayflowUserPrincipal principal,
            @PathVariable String id
    ) {
        String merchantId = getRequiredMerchantId(principal);
        return ResponseEntity.ok(paymentService.cancelPayment(id, merchantId, principal.getUsername()));
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Inquire provider and resolve an UNKNOWN timeout payment")
    public ResponseEntity<PaymentDto> resolveUnknown(@PathVariable String id) {
        return ResponseEntity.ok(paymentService.resolveUnknownPayment(id));
    }

    private String getRequiredMerchantId(PayflowUserPrincipal principal) {
        if (principal == null || principal.getMerchantId() == null) {
            throw new ForbiddenException("Operation requires an authenticated merchant context.");
        }
        return principal.getMerchantId();
    }
}

