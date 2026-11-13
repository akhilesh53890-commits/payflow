package com.payflow.merchant.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "merchant_id", nullable = false, length = 36)
    private String merchantId;

    @Column(name = "public_key", nullable = false, unique = true, length = 64)
    private String publicKey;

    @Column(name = "hashed_secret", nullable = false, length = 128)
    private String hashedSecret;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public ApiKey() {}

    public ApiKey(String merchantId, String publicKey, String hashedSecret, String name, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.merchantId = merchantId;
        this.publicKey = publicKey;
        this.hashedSecret = hashedSecret;
        this.name = name;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public String getMerchantId() { return merchantId; }
    public String getPublicKey() { return publicKey; }
    public String getHashedSecret() { return hashedSecret; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}

