package com.payflow.merchant.repository;

import com.payflow.merchant.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {
    Optional<ApiKey> findByPublicKey(String publicKey);
    List<ApiKey> findByMerchantId(String merchantId);
    Optional<ApiKey> findByIdAndMerchantId(String id, String merchantId);
}

