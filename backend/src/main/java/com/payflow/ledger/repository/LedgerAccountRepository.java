package com.payflow.ledger.repository;

import com.payflow.ledger.domain.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, String> {
    Optional<LedgerAccount> findByAccountNumber(String accountNumber);
    List<LedgerAccount> findByMerchantId(String merchantId);
    List<LedgerAccount> findByMerchantIdIsNull();
}

