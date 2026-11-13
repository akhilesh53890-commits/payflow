package com.payflow.risk.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_assessments")
public class RiskAssessment {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "payment_id", nullable = false, length = 36)
    private String paymentId;

    @Column(name = "merchant_id", nullable = false, length = 36)
    private String merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 32)
    private RiskDecision decision;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "reasons", columnDefinition = "TEXT")
    private String reasons;

    @Column(name = "rule_versions", nullable = false, length = 64)
    private String ruleVersions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RiskAssessment() {}

    public RiskAssessment(String paymentId, String merchantId, RiskDecision decision, int score, String reasons, String ruleVersions) {
        this.id = UUID.randomUUID().toString();
        this.paymentId = paymentId;
        this.merchantId = merchantId;
        this.decision = decision;
        this.score = score;
        this.reasons = reasons;
        this.ruleVersions = ruleVersions;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getPaymentId() { return paymentId; }
    public String getMerchantId() { return merchantId; }
    public RiskDecision getDecision() { return decision; }
    public int getScore() { return score; }
    public String getReasons() { return reasons; }
    public String getRuleVersions() { return ruleVersions; }
    public Instant getCreatedAt() { return createdAt; }
}

