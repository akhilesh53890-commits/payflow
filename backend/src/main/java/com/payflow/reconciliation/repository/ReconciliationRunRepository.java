package com.payflow.reconciliation.repository;

import com.payflow.reconciliation.domain.ReconciliationRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, String> {
    Page<ReconciliationRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}

