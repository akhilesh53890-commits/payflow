package com.payflow.idempotency.repository;

import com.payflow.idempotency.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {
    Optional<IdempotencyRecord> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);
}

