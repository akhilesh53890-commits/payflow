package com.payflow.merchant.service;

import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.exception.UnauthorizedException;
import com.payflow.merchant.domain.ApiKey;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.dto.ApiKeyDtos.ApiKeyCreatedResponse;
import com.payflow.merchant.dto.ApiKeyDtos.ApiKeyDto;
import com.payflow.merchant.repository.ApiKeyRepository;
import com.payflow.merchant.repository.MerchantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final MerchantRepository merchantRepository;
    private final String pepper;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            MerchantRepository merchantRepository,
            @Value("${payflow.security.api-key.pepper}") String pepper
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.merchantRepository = merchantRepository;
        this.pepper = pepper;
    }

    @Transactional
    public ApiKeyCreatedResponse createApiKey(String merchantId, String name, Instant expiresAt) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found: " + merchantId));

        String prefix = merchant.isSandboxMode() ? "test_" : "live_";
        String publicKey = "pk_" + prefix + randomString(24);
        String rawSecret = "sk_" + prefix + randomString(32);
        String hashedSecret = hashSecret(rawSecret);

        ApiKey apiKey = new ApiKey(merchantId, publicKey, hashedSecret, name, expiresAt);
        ApiKey saved = apiKeyRepository.save(apiKey);

        return ApiKeyCreatedResponse.of(saved, rawSecret);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyDto> listApiKeys(String merchantId) {
        return apiKeyRepository.findByMerchantId(merchantId).stream()
                .map(ApiKeyDto::from)
                .toList();
    }

    @Transactional
    public void revokeApiKey(String merchantId, String apiKeyId) {
        ApiKey apiKey = apiKeyRepository.findByIdAndMerchantId(apiKeyId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("API Key not found: " + apiKeyId));

        apiKey.setStatus("REVOKED");
        apiKey.setRevokedAt(Instant.now());
        apiKeyRepository.save(apiKey);
    }

    @Transactional(readOnly = true)
    public Optional<Merchant> authenticateApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Optional.empty();
        }

        // Support either composite "pk_test_xxx:sk_test_yyy" or bearer secret key
        String publicKey;
        String secretKey;

        if (rawApiKey.contains(":")) {
            String[] parts = rawApiKey.split(":", 2);
            publicKey = parts[0].trim();
            secretKey = parts[1].trim();

            Optional<ApiKey> optKey = apiKeyRepository.findByPublicKey(publicKey);
            if (optKey.isEmpty()) {
                return Optional.empty();
            }
            ApiKey apiKey = optKey.get();
            if (!"ACTIVE".equals(apiKey.getStatus())) {
                return Optional.empty();
            }
            if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(Instant.now())) {
                return Optional.empty();
            }

            String computedHash = hashSecret(secretKey);
            if (!MessageDigest.isEqual(computedHash.getBytes(StandardCharsets.UTF_8),
                    apiKey.getHashedSecret().getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }

            return merchantRepository.findById(apiKey.getMerchantId());
        } else {
            // Direct secret key lookup: verify against stored hashes
            String incomingHash = hashSecret(rawApiKey.trim());
            // Lookup matching active key by iterating or indexed lookup
            for (ApiKey key : apiKeyRepository.findAll()) {
                if ("ACTIVE".equals(key.getStatus()) && MessageDigest.isEqual(
                        incomingHash.getBytes(StandardCharsets.UTF_8),
                        key.getHashedSecret().getBytes(StandardCharsets.UTF_8))) {
                    if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(Instant.now())) {
                        return Optional.empty();
                    }
                    return merchantRepository.findById(key.getMerchantId());
                }
            }
            return Optional.empty();
        }
    }

    public String hashSecret(String rawSecret) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(secretKeySpec);
            byte[] hash = hmac.doFinal(rawSecret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash API secret key", e);
        }
    }

    private String randomString(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }
}

