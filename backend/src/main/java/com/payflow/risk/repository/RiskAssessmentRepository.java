package com.payflow.risk.repository;

import com.payflow.risk.domain.RiskAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, String> {
    Optional<RiskAssessment> findByPaymentId(String paymentId);
    Page<RiskAssessment> findByMerchantIdOrderByCreatedAtDesc(String merchantId, Pageable pageable);
}

