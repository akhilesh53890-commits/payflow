package com.payflow.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 32)
    private ReferenceType referenceType;

    @Column(name = "reference_id", nullable = false, length = 36)
    private String referenceId;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public LedgerTransaction() {}

    public LedgerTransaction(ReferenceType referenceType, String referenceId, String description, String currency) {
        this.id = UUID.randomUUID().toString();
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.description = description;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public ReferenceType getReferenceType() { return referenceType; }
    public String getReferenceId() { return referenceId; }
    public String getDescription() { return description; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}

