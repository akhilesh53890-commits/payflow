package com.payflow.webhook.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "merchant_id", nullable = false, length = 36)
    private String merchantId;

    @Column(name = "url", nullable = false, length = 512)
    private String url;

    @Column(name = "secret", nullable = false, length = 128)
    private String secret;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public WebhookEndpoint() {}

    public WebhookEndpoint(String merchantId, String url, String secret) {
        this.id = UUID.randomUUID().toString();
        this.merchantId = merchantId;
        this.url = url;
        this.secret = secret;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getMerchantId() { return merchantId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSecret() { return secret; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}

