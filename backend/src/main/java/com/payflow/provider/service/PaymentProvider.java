package com.payflow.provider.service;

import com.payflow.provider.domain.ProviderDtos.*;

public interface PaymentProvider {
    String getName();
    ProviderResponse authorizeAndCapture(ProviderPaymentRequest request);
    ProviderRefundResponse refund(ProviderRefundRequest request);
    ProviderResponse inquirePaymentStatus(String providerReference);
}

