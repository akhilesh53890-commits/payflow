package com.payflow.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.audit.service.AuditService;
import com.payflow.common.MoneyUtils;
import com.payflow.common.exception.ConflictException;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.exception.ValidationException;
import com.payflow.eventing.service.OutboxService;
import com.payflow.idempotency.service.IdempotencyService;
import com.payflow.ledger.domain.AccountType;
import com.payflow.ledger.domain.EntryType;
import com.payflow.ledger.domain.ReferenceType;
import com.payflow.ledger.dto.LedgerDtos.EntryRequest;
import com.payflow.ledger.dto.LedgerDtos.PostJournalCommand;
import com.payflow.ledger.service.LedgerService;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.domain.PaymentState;
import com.payflow.payment.domain.PaymentStateTransition;
import com.payflow.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.payflow.payment.dto.PaymentDtos.PaymentDetailDto;
import com.payflow.payment.dto.PaymentDtos.PaymentDto;
import com.payflow.payment.repository.PaymentAttemptRepository;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.repository.PaymentStateTransitionRepository;
import com.payflow.provider.domain.ProviderDtos.ProviderPaymentRequest;
import com.payflow.provider.domain.ProviderDtos.ProviderResponse;
import com.payflow.provider.domain.ProviderOutcome;
import com.payflow.provider.service.PaymentProvider;
import com.payflow.provider.service.ProviderRouter;
import com.payflow.risk.domain.RiskAssessment;
import com.payflow.risk.domain.RiskDecision;
import com.payflow.risk.service.RiskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentStateTransitionRepository transitionRepository;
    private final IdempotencyService idempotencyService;
    private final ProviderRouter providerRouter;
    private final LedgerService ledgerService;
    private final RiskEngine riskEngine;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository attemptRepository,
            PaymentStateTransitionRepository transitionRepository,
            IdempotencyService idempotencyService,
            ProviderRouter providerRouter,
            LedgerService ledgerService,
            RiskEngine riskEngine,
            OutboxService outboxService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.transitionRepository = transitionRepository;
        this.idempotencyService = idempotencyService;
        this.providerRouter = providerRouter;
        this.ledgerService = ledgerService;
        this.riskEngine = riskEngine;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentDto processPayment(String merchantId, String idempotencyKey, CreatePaymentRequest request, String actorId, String ipAddress) {
        // 1. Durable Idempotency Check
        Optional<IdempotencyService.CachedResponse> cached = idempotencyService.startOrCheck(merchantId, idempotencyKey, request);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get().responseBody(), PaymentDto.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize cached idempotency response: {}", e.getMessage());
            }
        }

        try {
            BigDecimal formattedAmount = MoneyUtils.format(request.amount());

            // 2. Initialize Payment in CREATED state
            Payment payment = new Payment(
                    merchantId,
                    formattedAmount,
                    request.currency(),
                    request.customerEmail(),
                    request.description(),
                    request.paymentMethod()
            );
            Payment savedPayment = paymentRepository.save(payment);
            recordTransition(savedPayment.getId(), null, PaymentState.CREATED, "Payment initialized", actorId);

            // 3. Risk Assessment
            RiskAssessment risk = riskEngine.evaluateAndRecord(
                    savedPayment.getId(), merchantId, savedPayment.getAmount(), savedPayment.getCurrency(), savedPayment.getCustomerEmail());

            if (risk.getDecision() == RiskDecision.REJECT) {
                savedPayment.setStatus(PaymentState.FAILED);
                savedPayment.setFailureReason("Rejected by risk engine: " + risk.getReasons());
                savedPayment.setUpdatedAt(Instant.now());
                paymentRepository.save(savedPayment);

                recordTransition(savedPayment.getId(), PaymentState.CREATED, PaymentState.FAILED, "Risk Rejection", "SYSTEM");
                outboxService.recordEvent("Payment", savedPayment.getId(), "PAYMENT_FAILED", PaymentDto.from(savedPayment));

                PaymentDto dto = PaymentDto.from(savedPayment);
                completeIdempotency(merchantId, idempotencyKey, 200, dto);
                return dto;
            }

            // 4. Transition to PROCESSING
            savedPayment.setStatus(PaymentState.PROCESSING);
            savedPayment.setUpdatedAt(Instant.now());
            paymentRepository.save(savedPayment);
            recordTransition(savedPayment.getId(), PaymentState.CREATED, PaymentState.PROCESSING, "Dispatching to provider", actorId);

            // 5. Provider Dispatch
            ProviderPaymentRequest providerReq = new ProviderPaymentRequest(
                    savedPayment.getId(),
                    merchantId,
                    savedPayment.getAmount(),
                    savedPayment.getCurrency(),
                    savedPayment.getPaymentMethod(),
                    savedPayment.getCustomerEmail(),
                    idempotencyKey
            );

            PaymentProvider provider = providerRouter.selectProvider(providerReq);
            savedPayment.setProviderUsed(provider.getName());

            ProviderResponse providerResp = provider.authorizeAndCapture(providerReq);

            // Record Attempt
            PaymentAttempt attempt = new PaymentAttempt(
                    savedPayment.getId(),
                    provider.getName(),
                    idempotencyKey,
                    providerResp.outcome().name(),
                    objectMapper.writeValueAsString(providerReq),
                    providerResp.rawResponse(),
                    Integer.parseInt(providerResp.code().matches("\\d+") ? providerResp.code() : "500"),
                    providerResp.message()
            );
            attemptRepository.save(attempt);

            // 6. Handle Provider Outcome
            if (providerResp.outcome() == ProviderOutcome.DEFINITIVE_SUCCESS) {
                savedPayment.setStatus(PaymentState.SUCCEEDED);
                savedPayment.setProviderReference(providerResp.providerReference());
                savedPayment.setUpdatedAt(Instant.now());
                paymentRepository.save(savedPayment);
                recordTransition(savedPayment.getId(), PaymentState.PROCESSING, PaymentState.SUCCEEDED, "Provider capture successful", provider.getName());

                // Post Balanced Double-Entry Ledger Journal
                postPaymentLedgerJournal(savedPayment);

                // Transactional Outbox Event
                outboxService.recordEvent("Payment", savedPayment.getId(), "PAYMENT_SUCCEEDED", PaymentDto.from(savedPayment));

            } else if (providerResp.outcome() == ProviderOutcome.DEFINITIVE_DECLINE) {
                savedPayment.setStatus(PaymentState.FAILED);
                savedPayment.setFailureReason("Provider declined: " + providerResp.message());
                savedPayment.setUpdatedAt(Instant.now());
                paymentRepository.save(savedPayment);
                recordTransition(savedPayment.getId(), PaymentState.PROCESSING, PaymentState.FAILED, "Provider decline", provider.getName());

                outboxService.recordEvent("Payment", savedPayment.getId(), "PAYMENT_FAILED", PaymentDto.from(savedPayment));

            } else if (providerResp.outcome() == ProviderOutcome.AMBIGUOUS_TIMEOUT) {
                // NEVER blindly retry or fail over! Transition to UNKNOWN for explicit reconciliation
                savedPayment.setStatus(PaymentState.UNKNOWN);
                savedPayment.setFailureReason("Ambiguous provider timeout: " + providerResp.message());
                savedPayment.setUpdatedAt(Instant.now());
                paymentRepository.save(savedPayment);
                recordTransition(savedPayment.getId(), PaymentState.PROCESSING, PaymentState.UNKNOWN, "Ambiguous Provider Timeout", provider.getName());

                outboxService.recordEvent("Payment", savedPayment.getId(), "PAYMENT_AMBIGUOUS_TIMEOUT", PaymentDto.from(savedPayment));

            } else { // UNAVAILABLE / OTHER
                savedPayment.setStatus(PaymentState.UNKNOWN);
                savedPayment.setFailureReason("Provider unavailable: " + providerResp.message());
                savedPayment.setUpdatedAt(Instant.now());
                paymentRepository.save(savedPayment);
                recordTransition(savedPayment.getId(), PaymentState.PROCESSING, PaymentState.UNKNOWN, "Provider Unavailable", provider.getName());

                outboxService.recordEvent("Payment", savedPayment.getId(), "PAYMENT_UNKNOWN_OUTCOME", PaymentDto.from(savedPayment));
            }

            auditService.record(actorId, "MERCHANT", "PROCESS_PAYMENT", "Payment", savedPayment.getId(),
                    savedPayment.getStatus().name(), ipAddress, idempotencyKey, "amount=" + savedPayment.getAmount());

            PaymentDto dto = PaymentDto.from(savedPayment);
            completeIdempotency(merchantId, idempotencyKey, 201, dto);
            return dto;

        } catch (Exception ex) {
            log.error("Payment processing error for key={}: {}", idempotencyKey, ex.getMessage(), ex);
            idempotencyService.fail(merchantId, idempotencyKey);
            throw ex;
        }
    }

    @Transactional
    public PaymentDto cancelPayment(String paymentId, String merchantId, String actorId) {
        Payment payment = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        if (!payment.getStatus().canTransitionTo(PaymentState.CANCELLED)) {
            throw new ConflictException("CANNOT_CANCEL_PAYMENT", "Payment in state " + payment.getStatus() + " cannot be cancelled.");
        }

        PaymentState oldState = payment.getStatus();
        payment.setStatus(PaymentState.CANCELLED);
        payment.setUpdatedAt(Instant.now());
        Payment saved = paymentRepository.save(payment);

        recordTransition(payment.getId(), oldState, PaymentState.CANCELLED, "Merchant cancellation", actorId);
        outboxService.recordEvent("Payment", payment.getId(), "PAYMENT_CANCELLED", PaymentDto.from(saved));

        return PaymentDto.from(saved);
    }

    @Transactional
    public PaymentDto resolveUnknownPayment(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentState.UNKNOWN) {
            throw new ConflictException("PAYMENT_NOT_UNKNOWN", "Payment is not in UNKNOWN status: " + payment.getStatus());
        }

        PaymentProvider provider = providerRouter.getProviderByName(
                payment.getProviderUsed() != null ? payment.getProviderUsed() : "STRIPE");

        ProviderResponse inquiry = provider.inquirePaymentStatus(payment.getProviderReference());

        if (inquiry.outcome() == ProviderOutcome.DEFINITIVE_SUCCESS) {
            payment.setStatus(PaymentState.SUCCEEDED);
            payment.setFailureReason(null);
            payment.setUpdatedAt(Instant.now());
            Payment saved = paymentRepository.save(payment);

            recordTransition(saved.getId(), PaymentState.UNKNOWN, PaymentState.SUCCEEDED, "Reconciliation inquiry confirmed success", "RECONCILIATION");
            postPaymentLedgerJournal(saved);
            outboxService.recordEvent("Payment", saved.getId(), "PAYMENT_SUCCEEDED", PaymentDto.from(saved));
            return PaymentDto.from(saved);
        } else {
            payment.setStatus(PaymentState.FAILED);
            payment.setFailureReason("Reconciliation inquiry resolved to DECLINE: " + inquiry.message());
            payment.setUpdatedAt(Instant.now());
            Payment saved = paymentRepository.save(payment);

            recordTransition(saved.getId(), PaymentState.UNKNOWN, PaymentState.FAILED, "Reconciliation inquiry confirmed decline", "RECONCILIATION");
            outboxService.recordEvent("Payment", saved.getId(), "PAYMENT_FAILED", PaymentDto.from(saved));
            return PaymentDto.from(saved);
        }
    }

    @Transactional(readOnly = true)
    public PaymentDetailDto getPaymentDetail(String paymentId, String merchantId) {
        Payment payment;
        if (merchantId != null) {
            payment = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        } else {
            payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        }

        List<PaymentAttempt> attempts = attemptRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
        List<PaymentStateTransition> transitions = transitionRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);

        return new PaymentDetailDto(PaymentDto.from(payment), attempts, transitions);
    }

    @Transactional(readOnly = true)
    public Page<PaymentDto> listPayments(String merchantId, PaymentState status, Pageable pageable) {
        Page<Payment> page;
        if (merchantId != null) {
            if (status != null) {
                page = paymentRepository.findByMerchantIdAndStatusOrderByCreatedAtDesc(merchantId, status, pageable);
            } else {
                page = paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable);
            }
        } else {
            page = paymentRepository.findAll(pageable);
        }
        return page.map(PaymentDto::from);
    }

    private void postPaymentLedgerJournal(Payment payment) {
        String currency = payment.getCurrency();
        String custAcctNum = "1010-CUST-FUNDS-" + currency;
        String merchAcctNum = "2010-MERCHANT-" + payment.getMerchantId() + "-" + currency;

        // Ensure accounts exist
        ledgerService.getOrCreateAccount(null, custAcctNum, "Customer Transit Funds " + currency, AccountType.ASSET, currency);
        ledgerService.getOrCreateAccount(payment.getMerchantId(), merchAcctNum, "Merchant Settlement " + currency, AccountType.LIABILITY, currency);

        // Balanced Journal: DEBIT Asset (Customer Funds), CREDIT Liability (Merchant Settlement)
        PostJournalCommand journalCommand = new PostJournalCommand(
                ReferenceType.PAYMENT,
                payment.getId(),
                "Captured payment " + payment.getId(),
                currency,
                List.of(
                        new EntryRequest(custAcctNum, EntryType.DEBIT, payment.getAmount(), currency),
                        new EntryRequest(merchAcctNum, EntryType.CREDIT, payment.getAmount(), currency)
                )
        );

        ledgerService.postJournal(journalCommand);
    }

    private void recordTransition(String paymentId, PaymentState from, PaymentState to, String reason, String actor) {
        PaymentStateTransition transition = new PaymentStateTransition(paymentId, from, to, reason, actor);
        transitionRepository.save(transition);
    }

    private void completeIdempotency(String merchantId, String idempotencyKey, int status, PaymentDto dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            idempotencyService.complete(merchantId, idempotencyKey, status, json);
        } catch (Exception e) {
            log.warn("Failed to serialize payment DTO for idempotency completion: {}", e.getMessage());
        }
    }
}

