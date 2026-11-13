package com.payflow.webhook.repository;

import com.payflow.webhook.domain.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, String> {
    List<WebhookEndpoint> findByMerchantId(String merchantId);
    Optional<WebhookEndpoint> findByMerchantIdAndStatus(String merchantId, String status);
}

