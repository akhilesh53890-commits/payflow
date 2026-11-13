package com.payflow.provider.domain;

import java.math.BigDecimal;

public class ProviderDtos {

    public record ProviderPaymentRequest(
            String paymentId,
            String merchantId,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            String customerEmail,
            String idempotencyKey
    ) {}

    public record ProviderResponse(
            ProviderOutcome outcome,
            String providerReference,
            String code,
            String message,
            String rawResponse
    ) {
        public static ProviderResponse success(String providerReference, String message, String raw) {
            return new ProviderResponse(ProviderOutcome.DEFINITIVE_SUCCESS, providerReference, "200", message, raw);
        }

        public static ProviderResponse decline(String code, String message, String raw) {
            return new ProviderResponse(ProviderOutcome.DEFINITIVE_DECLINE, null, code, message, raw);
        }

        public static ProviderResponse timeout(String message) {
            return new ProviderResponse(ProviderOutcome.AMBIGUOUS_TIMEOUT, null, "TIMEOUT", message, "{\"error\": \"GATEWAY_TIMEOUT\"}");
        }

        public static ProviderResponse unavailable(String message) {
            return new ProviderResponse(ProviderOutcome.UNAVAILABLE, null, "503", message, "{\"error\": \"SERVICE_UNAVAILABLE\"}");
        }
    }

    public record ProviderRefundRequest(
            String refundId,
            String paymentId,
            String providerReference,
            BigDecimal amount,
            String currency,
            String reason
    ) {}

    public record ProviderRefundResponse(
            ProviderOutcome outcome,
            String providerRefundId,
            String message
    ) {}
}

