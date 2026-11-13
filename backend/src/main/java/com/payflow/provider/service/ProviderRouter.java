package com.payflow.provider.service;

import com.payflow.provider.domain.ProviderDtos.*;
import com.payflow.provider.domain.ProviderOutcome;
import com.payflow.provider.service.simulator.AdyenSimulator;
import com.payflow.provider.service.simulator.StripeSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProviderRouter {

    private static final Logger log = LoggerFactory.getLogger(ProviderRouter.class);

    private final Map<String, PaymentProvider> providers;
    private final StripeSimulator stripeSimulator;
    private final AdyenSimulator adyenSimulator;

    public ProviderRouter(List<PaymentProvider> providerList, StripeSimulator stripeSimulator, AdyenSimulator adyenSimulator) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(p -> p.getName().toUpperCase(), Function.identity()));
        this.stripeSimulator = stripeSimulator;
        this.adyenSimulator = adyenSimulator;
    }

    public PaymentProvider selectProvider(ProviderPaymentRequest request) {
        // Smart Routing Rule: EUR routed to Adyen, others to Stripe by default
        if ("EUR".equalsIgnoreCase(request.currency())) {
            return adyenSimulator;
        }
        return stripeSimulator;
    }

    public PaymentProvider getProviderByName(String name) {
        PaymentProvider provider = providers.get(name.toUpperCase());
        if (provider == null) {
            log.warn("Requested provider '{}' not found, defaulting to STRIPE", name);
            return stripeSimulator;
        }
        return provider;
    }
}

