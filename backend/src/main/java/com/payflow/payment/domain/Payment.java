package com.payflow.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "merchant_id", nullable = false, length = 36)
    private String merchantId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentState status;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "payment_method", length = 64)
    private String paymentMethod;

    @Column(name = "provider_used", length = 64)
    private String providerUsed;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Payment() {}

    public Payment(String merchantId, BigDecimal amount, String currency, String customerEmail, String description, String paymentMethod) {
        this.id = UUID.randomUUID().toString();
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency.toUpperCase();
        this.customerEmail = customerEmail;
        this.description = description;
        this.paymentMethod = (paymentMethod != null) ? paymentMethod : "CARD";
        this.status = PaymentState.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getMerchantId() { return merchantId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentState getStatus() { return status; }
    public void setStatus(PaymentState status) { this.status = status; }
    public String getCustomerEmail() { return customerEmail; }
    public String getDescription() { return description; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getProviderUsed() { return providerUsed; }
    public void setProviderUsed(String providerUsed) { this.providerUsed = providerUsed; }
    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
}

