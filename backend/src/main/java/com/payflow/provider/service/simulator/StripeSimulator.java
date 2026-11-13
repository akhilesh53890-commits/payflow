package com.payflow.provider.service.simulator;

import com.payflow.provider.domain.ProviderDtos.*;
import com.payflow.provider.domain.ProviderOutcome;
import com.payflow.provider.service.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StripeSimulator implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(StripeSimulator.class);
    private final ConcurrentHashMap<String, ProviderResponse> memoryLedger = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "STRIPE";
    }

    @Override
    public ProviderResponse authorizeAndCapture(ProviderPaymentRequest request) {
        log.info("[StripeSimulator] Processing paymentId={} amount={} {}", request.paymentId(), request.amount(), request.currency());

        BigDecimal amount = request.amount();
        String amountStr = amount.toPlainString();

        // Deterministic simulation rules based on amount cents
        if (amountStr.endsWith(".99")) {
            log.warn("[StripeSimulator] Simulating DECLINE for amount={}", amount);
            return ProviderResponse.decline("card_declined", "Card was declined: Insufficient funds",
                    "{\"error\": {\"code\": \"card_declined\", \"decline_code\": \"insufficient_funds\"}}");
        }

        if (amountStr.endsWith(".54")) {
            log.warn("[StripeSimulator] Simulating AMBIGUOUS TIMEOUT for amount={}", amount);
            return ProviderResponse.timeout("Read timed out after 5000ms connecting to Stripe API");
        }

        if (amountStr.endsWith(".53")) {
            log.warn("[StripeSimulator] Simulating 503 SERVICE UNAVAILABLE for amount={}", amount);
            return ProviderResponse.unavailable("Stripe service temporarily unavailable (HTTP 503)");
        }

        String ref = "ch_stripe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        ProviderResponse response = ProviderResponse.success(ref, "Charge successful",
                "{\"id\": \"" + ref + "\", \"status\": \"succeeded\", \"captured\": true}");

        memoryLedger.put(ref, response);
        return response;
    }

    @Override
    public ProviderRefundResponse refund(ProviderRefundRequest request) {
        log.info("[StripeSimulator] Processing refund for paymentId={} amount={}", request.paymentId(), request.amount());
        String refundRef = "re_stripe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return new ProviderRefundResponse(ProviderOutcome.DEFINITIVE_SUCCESS, refundRef, "Refund succeeded");
    }

    @Override
    public ProviderResponse inquirePaymentStatus(String providerReference) {
        log.info("[StripeSimulator] Inquiring payment status for providerReference={}", providerReference);
        if (providerReference != null && memoryLedger.containsKey(providerReference)) {
            return memoryLedger.get(providerReference);
        }
        if (providerReference != null && providerReference.startsWith("ch_stripe_")) {
            return ProviderResponse.success(providerReference, "Charge confirmed succeeded via reconciliation inquiry", "{}");
        }
        return ProviderResponse.decline("not_found", "Transaction not found on Stripe", "{}");
    }
}

