package com.payflow.reconciliation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "records_analyzed", nullable = false)
    private int recordsAnalyzed;

    @Column(name = "discrepancies_found", nullable = false)
    private int discrepanciesFound;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public ReconciliationRun() {}

    public ReconciliationRun(String status) {
        this.id = UUID.randomUUID().toString();
        this.status = status;
        this.recordsAnalyzed = 0;
        this.discrepanciesFound = 0;
        this.startedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRecordsAnalyzed() { return recordsAnalyzed; }
    public void setRecordsAnalyzed(int recordsAnalyzed) { this.recordsAnalyzed = recordsAnalyzed; }
    public int getDiscrepanciesFound() { return discrepanciesFound; }
    public void setDiscrepanciesFound(int discrepanciesFound) { this.discrepanciesFound = discrepanciesFound; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}

