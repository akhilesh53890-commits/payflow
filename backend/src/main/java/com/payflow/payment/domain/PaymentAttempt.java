package com.payflow.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "payment_id", nullable = false, length = 36)
    private String paymentId;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PaymentAttempt() {}

    public PaymentAttempt(String paymentId, String provider, String idempotencyKey, String status,
                          String requestPayload, String responsePayload, Integer httpStatus, String errorMessage) {
        this.id = UUID.randomUUID().toString();
        this.paymentId = paymentId;
        this.provider = provider;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.requestPayload = requestPayload;
        this.responsePayload = responsePayload;
        this.httpStatus = httpStatus;
        this.errorMessage = errorMessage;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getPaymentId() { return paymentId; }
    public String getProvider() { return provider; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public String getRequestPayload() { return requestPayload; }
    public String getResponsePayload() { return responsePayload; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}

