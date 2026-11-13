package com.payflow.webhook.repository;

import com.payflow.webhook.domain.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, String> {
    Page<WebhookDelivery> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT d FROM WebhookDelivery d WHERE d.status = 'PENDING' AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now)")
    List<WebhookDelivery> findPendingRetries(Instant now, Pageable pageable);
}

