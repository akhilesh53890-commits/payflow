package com.payflow.merchant.controller;

import com.payflow.common.exception.ForbiddenException;
import com.payflow.identity.security.PayflowUserPrincipal;
import com.payflow.merchant.dto.ApiKeyDtos.ApiKeyCreatedResponse;
import com.payflow.merchant.dto.ApiKeyDtos.ApiKeyDto;
import com.payflow.merchant.dto.ApiKeyDtos.CreateApiKeyRequest;
import com.payflow.merchant.dto.MerchantDto;
import com.payflow.merchant.service.ApiKeyService;
import com.payflow.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/merchants")
@Tag(name = "Merchant Management", description = "Endpoints for merchant profile and scoped API keys")
public class MerchantController {

    private final MerchantService merchantService;
    private final ApiKeyService apiKeyService;

    public MerchantController(MerchantService merchantService, ApiKeyService apiKeyService) {
        this.merchantService = merchantService;
        this.apiKeyService = apiKeyService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated merchant profile")
    public ResponseEntity<MerchantDto> me(@AuthenticationPrincipal PayflowUserPrincipal principal) {
        String merchantId = getRequiredMerchantId(principal);
        return ResponseEntity.ok(merchantService.getMerchantDto(merchantId));
    }

    @PatchMapping("/me/webhook")
    @Operation(summary = "Configure outbound webhook endpoint URL")
    public ResponseEntity<MerchantDto> updateWebhook(
            @AuthenticationPrincipal PayflowUserPrincipal principal,
            @RequestBody Map<String, String> payload
    ) {
        String merchantId = getRequiredMerchantId(principal);
        String webhookUrl = payload.get("webhookUrl");
        return ResponseEntity.ok(merchantService.updateWebhookConfig(merchantId, webhookUrl));
    }

    @PostMapping("/me/toggle-sandbox")
    @Operation(summary = "Toggle merchant between sandbox and live mode")
    public ResponseEntity<MerchantDto> toggleSandbox(@AuthenticationPrincipal PayflowUserPrincipal principal) {
        String merchantId = getRequiredMerchantId(principal);
        return ResponseEntity.ok(merchantService.toggleSandboxMode(merchantId));
    }

    @GetMapping("/api-keys")
    @Operation(summary = "List all API keys for current merchant")
    public ResponseEntity<List<ApiKeyDto>> listApiKeys(@AuthenticationPrincipal PayflowUserPrincipal principal) {
        String merchantId = getRequiredMerchantId(principal);
        return ResponseEntity.ok(apiKeyService.listApiKeys(merchantId));
    }

    @PostMapping("/api-keys")
    @Operation(summary = "Generate a new scoped API key with one-time secret display")
    public ResponseEntity<ApiKeyCreatedResponse> createApiKey(
            @AuthenticationPrincipal PayflowUserPrincipal principal,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        String merchantId = getRequiredMerchantId(principal);
        ApiKeyCreatedResponse response = apiKeyService.createApiKey(merchantId, request.name(), request.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/api-keys/{id}")
    @Operation(summary = "Revoke an API key immediately")
    public ResponseEntity<Void> revokeApiKey(
            @AuthenticationPrincipal PayflowUserPrincipal principal,
            @PathVariable String id
    ) {
        String merchantId = getRequiredMerchantId(principal);
        apiKeyService.revokeApiKey(merchantId, id);
        return ResponseEntity.noContent().build();
    }

    private String getRequiredMerchantId(PayflowUserPrincipal principal) {
        if (principal == null || principal.getMerchantId() == null) {
            throw new ForbiddenException("Operation requires an authenticated merchant context.");
        }
        return principal.getMerchantId();
    }
}

