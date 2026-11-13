package com.payflow.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_state_transitions")
public class PaymentStateTransition {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "payment_id", nullable = false, length = 36)
    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 32)
    private PaymentState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 32)
    private PaymentState toState;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "triggered_by", length = 64)
    private String triggeredBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PaymentStateTransition() {}

    public PaymentStateTransition(String paymentId, PaymentState fromState, PaymentState toState, String reason, String triggeredBy) {
        this.id = UUID.randomUUID().toString();
        this.paymentId = paymentId;
        this.fromState = fromState;
        this.toState = toState;
        this.reason = reason;
        this.triggeredBy = triggeredBy;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getPaymentId() { return paymentId; }
    public PaymentState getFromState() { return fromState; }
    public PaymentState getToState() { return toState; }
    public String getReason() { return reason; }
    public String getTriggeredBy() { return triggeredBy; }
    public Instant getCreatedAt() { return createdAt; }
}

