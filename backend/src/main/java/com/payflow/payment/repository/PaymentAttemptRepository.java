package com.payflow.payment.repository;

import com.payflow.payment.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, String> {
    List<PaymentAttempt> findByPaymentIdOrderByCreatedAtAsc(String paymentId);
}

