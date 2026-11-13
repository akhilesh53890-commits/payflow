package com.payflow.reconciliation.repository;

import com.payflow.reconciliation.domain.ReconciliationDiscrepancy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReconciliationDiscrepancyRepository extends JpaRepository<ReconciliationDiscrepancy, String> {
    List<ReconciliationDiscrepancy> findByReconciliationRunIdOrderByCreatedAtAsc(String runId);
}

