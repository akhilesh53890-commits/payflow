package com.payflow.merchant.dto;

import com.payflow.merchant.domain.ApiKey;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public class ApiKeyDtos {

    public record CreateApiKeyRequest(
            @NotBlank(message = "Key name is required")
            String name,
            Instant expiresAt
    ) {}

    public record ApiKeyCreatedResponse(
            String id,
            String name,
            String publicKey,
            String secretKey,
            String status,
            Instant createdAt,
            Instant expiresAt,
            String warning
    ) {
        public static ApiKeyCreatedResponse of(ApiKey apiKey, String rawSecret) {
            return new ApiKeyCreatedResponse(
                    apiKey.getId(),
                    apiKey.getName(),
                    apiKey.getPublicKey(),
                    rawSecret,
                    apiKey.getStatus(),
                    apiKey.getCreatedAt(),
                    apiKey.getExpiresAt(),
                    "Store this secret key safely. It will NEVER be shown again."
            );
        }
    }

    public record ApiKeyDto(
            String id,
            String name,
            String publicKey,
            String status,
            Instant createdAt,
            Instant revokedAt,
            Instant expiresAt
    ) {
        public static ApiKeyDto from(ApiKey apiKey) {
            return new ApiKeyDto(
                    apiKey.getId(),
                    apiKey.getName(),
                    apiKey.getPublicKey(),
                    apiKey.getStatus(),
                    apiKey.getCreatedAt(),
                    apiKey.getRevokedAt(),
                    apiKey.getExpiresAt()
            );
        }
    }
}

