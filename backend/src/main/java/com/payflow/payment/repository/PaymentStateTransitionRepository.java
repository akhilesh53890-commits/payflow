package com.payflow.payment.repository;

import com.payflow.payment.domain.PaymentStateTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentStateTransitionRepository extends JpaRepository<PaymentStateTransition, String> {
    List<PaymentStateTransition> findByPaymentIdOrderByCreatedAtAsc(String paymentId);
}

