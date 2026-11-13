package com.payflow.refund.repository;

import com.payflow.refund.domain.Refund;
import com.payflow.refund.domain.RefundState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, String> {
    List<Refund> findByPaymentIdOrderByCreatedAtAsc(String paymentId);
    Page<Refund> findByMerchantIdOrderByCreatedAtDesc(String merchantId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.paymentId = :paymentId AND r.status = 'SUCCEEDED'")
    BigDecimal sumSuccessfulRefundsByPaymentId(@Param("paymentId") String paymentId);
}

