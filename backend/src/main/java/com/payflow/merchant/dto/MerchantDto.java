package com.payflow.merchant.dto;

import com.payflow.merchant.domain.Merchant;

import java.time.Instant;

public record MerchantDto(
        String id,
        String userId,
        String businessName,
        String status,
        boolean sandboxMode,
        String webhookUrl,
        String webhookSecret,
        Instant createdAt
) {
    public static MerchantDto from(Merchant merchant) {
        return new MerchantDto(
                merchant.getId(),
                merchant.getUserId(),
                merchant.getBusinessName(),
                merchant.getStatus(),
                merchant.isSandboxMode(),
                merchant.getWebhookUrl(),
                merchant.getWebhookSecret(),
                merchant.getCreatedAt()
        );
    }
}

