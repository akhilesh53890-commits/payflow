package com.payflow.payment.repository;

import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    Page<Payment> findByMerchantIdOrderByCreatedAtDesc(String merchantId, Pageable pageable);
    Page<Payment> findByMerchantIdAndStatusOrderByCreatedAtDesc(String merchantId, PaymentState status, Pageable pageable);
    Optional<Payment> findByIdAndMerchantId(String id, String merchantId);
    long countByStatus(PaymentState status);
}

