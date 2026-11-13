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
public class AdyenSimulator implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(AdyenSimulator.class);
    private final ConcurrentHashMap<String, ProviderResponse> memoryLedger = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "ADYEN";
    }

    @Override
    public ProviderResponse authorizeAndCapture(ProviderPaymentRequest request) {
        log.info("[AdyenSimulator] Processing paymentId={} amount={} {}", request.paymentId(), request.amount(), request.currency());

        BigDecimal amount = request.amount();
        String amountStr = amount.toPlainString();

        if (amountStr.endsWith(".88")) {
            log.warn("[AdyenSimulator] Simulating DECLINE for amount={}", amount);
            return ProviderResponse.decline("Refused", "Adyen Refused: Fraud score exceeded",
                    "{\"resultCode\": \"Refused\", \"refusalReason\": \"FRAUD\"}");
        }

        if (amountStr.endsWith(".55")) {
            log.warn("[AdyenSimulator] Simulating AMBIGUOUS TIMEOUT for amount={}", amount);
            return ProviderResponse.timeout("Socket timeout waiting for Adyen gateway");
        }

        String ref = "psp_adyen_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        ProviderResponse response = ProviderResponse.success(ref, "Authorised",
                "{\"pspReference\": \"" + ref + "\", \"resultCode\": \"Authorised\"}");

        memoryLedger.put(ref, response);
        return response;
    }

    @Override
    public ProviderRefundResponse refund(ProviderRefundRequest request) {
        log.info("[AdyenSimulator] Processing refund for paymentId={} amount={}", request.paymentId(), request.amount());
        String refundRef = "refund_adyen_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return new ProviderRefundResponse(ProviderOutcome.DEFINITIVE_SUCCESS, refundRef, "[refund-received]");
    }

    @Override
    public ProviderResponse inquirePaymentStatus(String providerReference) {
        log.info("[AdyenSimulator] Inquiring payment status for providerReference={}", providerReference);
        if (providerReference != null && memoryLedger.containsKey(providerReference)) {
            return memoryLedger.get(providerReference);
        }
        if (providerReference != null && providerReference.startsWith("psp_adyen_")) {
            return ProviderResponse.success(providerReference, "Confirmed Authorised", "{}");
        }
        return ProviderResponse.decline("not_found", "Transaction not found on Adyen", "{}");
    }
}

