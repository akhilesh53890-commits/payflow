package com.payflow.reconciliation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_discrepancies")
public class ReconciliationDiscrepancy {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "reconciliation_run_id", nullable = false, length = 36)
    private String reconciliationRunId;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 36)
    private String entityId;

    @Column(name = "discrepancy_type", nullable = false, length = 64)
    private String discrepancyType;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ReconciliationDiscrepancy() {}

    public ReconciliationDiscrepancy(String reconciliationRunId, String entityType, String entityId,
                                     String discrepancyType, String severity, String details) {
        this.id = UUID.randomUUID().toString();
        this.reconciliationRunId = reconciliationRunId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.discrepancyType = discrepancyType;
        this.severity = severity;
        this.details = details;
        this.resolved = false;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getReconciliationRunId() { return reconciliationRunId; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getDiscrepancyType() { return discrepancyType; }
    public String getSeverity() { return severity; }
    public String getDetails() { return details; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
    public Instant getCreatedAt() { return createdAt; }
}

