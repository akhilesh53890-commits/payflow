package com.payflow.ledger.repository;

import com.payflow.ledger.domain.LedgerTransaction;
import com.payflow.ledger.domain.ReferenceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, String> {
    List<LedgerTransaction> findByReferenceTypeAndReferenceId(ReferenceType referenceType, String referenceId);
    Page<LedgerTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

