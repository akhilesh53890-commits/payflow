package com.payflow.risk.service;

import com.payflow.risk.domain.RiskAssessment;
import com.payflow.risk.domain.RiskDecision;
import com.payflow.risk.repository.RiskAssessmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class RiskEngine {

    private static final Logger log = LoggerFactory.getLogger(RiskEngine.class);
    private static final String RULE_VERSION = "rules_v1.2.0";

    private final RiskAssessmentRepository riskAssessmentRepository;

    public RiskEngine(RiskAssessmentRepository riskAssessmentRepository) {
        this.riskAssessmentRepository = riskAssessmentRepository;
    }

    public record RiskResult(
            RiskDecision decision,
            int score,
            List<String> reasons,
            String ruleVersion
    ) {}

    @Transactional
    public RiskAssessment evaluateAndRecord(String paymentId, String merchantId, BigDecimal amount, String currency, String customerEmail) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        // Rule 1: High Amount Threshold
        if (amount != null) {
            if (amount.compareTo(new BigDecimal("50000.00")) > 0) {
                score += 85;
                reasons.add("RULE_EXCESSIVE_AMOUNT: Transaction amount " + amount + " exceeds hard limit of 50,000");
            } else if (amount.compareTo(new BigDecimal("10000.00")) > 0) {
                score += 45;
                reasons.add("RULE_ELEVATED_AMOUNT: Transaction amount " + amount + " exceeds review threshold of 10,000");
            }
        }

        // Rule 2: Suspicious Email Domain
        if (customerEmail != null && (customerEmail.endsWith("@disposable.com") || customerEmail.endsWith("@tempmail.org"))) {
            score += 50;
            reasons.add("RULE_SUSPICIOUS_EMAIL_DOMAIN: Disposable or temporary email domain detected");
        }

        // Decision thresholds
        RiskDecision decision;
        if (score >= 80) {
            decision = RiskDecision.REJECT;
        } else if (score >= 40) {
            decision = RiskDecision.REVIEW;
        } else {
            decision = RiskDecision.APPROVE;
            reasons.add("RULE_STANDARD_CHECKS_PASSED: Normal transaction profile");
        }

        String reasonsStr = String.join("; ", reasons);
        RiskAssessment assessment = new RiskAssessment(paymentId, merchantId, decision, score, reasonsStr, RULE_VERSION);
        RiskAssessment saved = riskAssessmentRepository.save(assessment);

        log.info("Risk assessment for paymentId={}: decision={} score={} reasons='{}'",
                paymentId, decision, score, reasonsStr);

        return saved;
    }
}

