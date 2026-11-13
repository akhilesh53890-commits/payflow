package com.payflow.refund.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.audit.service.AuditService;
import com.payflow.common.MoneyUtils;
import com.payflow.common.exception.ConflictException;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.eventing.service.OutboxService;
import com.payflow.idempotency.service.IdempotencyService;
import com.payflow.ledger.domain.EntryType;
import com.payflow.ledger.domain.ReferenceType;
import com.payflow.ledger.dto.LedgerDtos.EntryRequest;
import com.payflow.ledger.dto.LedgerDtos.PostJournalCommand;
import com.payflow.ledger.service.LedgerService;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentState;
import com.payflow.payment.domain.PaymentStateTransition;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.repository.PaymentStateTransitionRepository;
import com.payflow.provider.domain.ProviderDtos.ProviderRefundRequest;
import com.payflow.provider.domain.ProviderDtos.ProviderRefundResponse;
import com.payflow.provider.domain.ProviderOutcome;
import com.payflow.provider.service.PaymentProvider;
import com.payflow.provider.service.ProviderRouter;
import com.payflow.refund.domain.Refund;
import com.payflow.refund.domain.RefundState;
import com.payflow.refund.dto.RefundDtos.CreateRefundRequest;
import com.payflow.refund.dto.RefundDtos.RefundDto;
import com.payflow.refund.repository.RefundRepository;
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
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentStateTransitionRepository transitionRepository;
    private final IdempotencyService idempotencyService;
    private final ProviderRouter providerRouter;
    private final LedgerService ledgerService;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RefundService(
            RefundRepository refundRepository,
            PaymentRepository paymentRepository,
            PaymentStateTransitionRepository transitionRepository,
            IdempotencyService idempotencyService,
            ProviderRouter providerRouter,
            LedgerService ledgerService,
            OutboxService outboxService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.transitionRepository = transitionRepository;
        this.idempotencyService = idempotencyService;
        this.providerRouter = providerRouter;
        this.ledgerService = ledgerService;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RefundDto processRefund(String merchantId, String idempotencyKey, CreateRefundRequest request, String actorId, String ipAddress) {
        // 1. Idempotency Check
        Optional<IdempotencyService.CachedResponse> cached = idempotencyService.startOrCheck(merchantId, idempotencyKey, request);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get().responseBody(), RefundDto.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize cached idempotency response: {}", e.getMessage());
            }
        }

        try {
            Payment payment = paymentRepository.findByIdAndMerchantId(request.paymentId(), merchantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + request.paymentId()));

            if (payment.getStatus() != PaymentState.SUCCEEDED && payment.getStatus() != PaymentState.PARTIALLY_REFUNDED) {
                throw new ConflictException("CANNOT_REFUND_PAYMENT",
                        "Payment in state " + payment.getStatus() + " cannot be refunded.");
            }

            BigDecimal requestedAmount = MoneyUtils.format(request.amount());
            BigDecimal alreadyRefunded = MoneyUtils.format(refundRepository.sumSuccessfulRefundsByPaymentId(payment.getId()));
            BigDecimal maxRefundable = payment.getAmount().subtract(alreadyRefunded);

            // Invariant: Total refunds cannot exceed captured payment amount
            if (requestedAmount.compareTo(maxRefundable) > 0) {
                throw new ConflictException("REFUND_EXCEEDS_CAPTURED_AMOUNT",
                        "Requested refund " + requestedAmount + " exceeds remaining refundable amount " + maxRefundable);
            }

            // 2. Initialize Refund in PENDING state
            Refund refund = new Refund(payment.getId(), merchantId, requestedAmount, payment.getCurrency(), request.reason());
            Refund savedRefund = refundRepository.save(refund);

            // 3. Provider Refund Dispatch
            PaymentProvider provider = providerRouter.getProviderByName(
                    payment.getProviderUsed() != null ? payment.getProviderUsed() : "STRIPE");

            ProviderRefundRequest providerReq = new ProviderRefundRequest(
                    savedRefund.getId(),
                    payment.getId(),
                    payment.getProviderReference(),
                    savedRefund.getAmount(),
                    savedRefund.getCurrency(),
                    request.reason()
            );

            ProviderRefundResponse providerResp = provider.refund(providerReq);

            if (providerResp.outcome() == ProviderOutcome.DEFINITIVE_SUCCESS) {
                savedRefund.setStatus(RefundState.SUCCEEDED);
                savedRefund.setProviderRefundId(providerResp.providerRefundId());
                savedRefund.setUpdatedAt(Instant.now());
                refundRepository.save(savedRefund);

                // Update Payment State
                BigDecimal newTotalRefunded = alreadyRefunded.add(requestedAmount);
                PaymentState targetState = (newTotalRefunded.compareTo(payment.getAmount()) >= 0)
                        ? PaymentState.REFUNDED
                        : PaymentState.PARTIALLY_REFUNDED;

                PaymentState oldState = payment.getStatus();
                payment.setStatus(targetState);
                payment.setUpdatedAt(Instant.now());
                paymentRepository.save(payment);

                PaymentStateTransition transition = new PaymentStateTransition(
                        payment.getId(), oldState, targetState, "Refund processed: " + requestedAmount, actorId);
                transitionRepository.save(transition);

                // Post Balancing Ledger Reversal Journal
                postRefundLedgerJournal(savedRefund);

                outboxService.recordEvent("Refund", savedRefund.getId(), "REFUND_COMPLETED", RefundDto.from(savedRefund));
            } else {
                savedRefund.setStatus(RefundState.FAILED);
                savedRefund.setUpdatedAt(Instant.now());
                refundRepository.save(savedRefund);

                outboxService.recordEvent("Refund", savedRefund.getId(), "REFUND_FAILED", RefundDto.from(savedRefund));
            }

            auditService.record(actorId, "MERCHANT", "PROCESS_REFUND", "Refund", savedRefund.getId(),
                    savedRefund.getStatus().name(), ipAddress, idempotencyKey, "amount=" + savedRefund.getAmount());

            RefundDto dto = RefundDto.from(savedRefund);
            try {
                idempotencyService.complete(merchantId, idempotencyKey, 201, objectMapper.writeValueAsString(dto));
            } catch (Exception ignored) {}

            return dto;

        } catch (Exception ex) {
            log.error("Refund processing error for key={}: {}", idempotencyKey, ex.getMessage(), ex);
            idempotencyService.fail(merchantId, idempotencyKey);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<RefundDto> listPaymentRefunds(String paymentId) {
        return refundRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId).stream()
                .map(RefundDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<RefundDto> listMerchantRefunds(String merchantId, Pageable pageable) {
        Page<Refund> page = (merchantId != null)
                ? refundRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable)
                : refundRepository.findAll(pageable);
        return page.map(RefundDto::from);
    }

    private void postRefundLedgerJournal(Refund refund) {
        String currency = refund.getCurrency();
        String custAcctNum = "1010-CUST-FUNDS-" + currency;
        String merchAcctNum = "2010-MERCHANT-" + refund.getMerchantId() + "-" + currency;

        // Reversal Journal: DEBIT Liability (Merchant Settlement), CREDIT Asset (Customer Funds)
        PostJournalCommand journalCommand = new PostJournalCommand(
                ReferenceType.REFUND,
                refund.getId(),
                "Refund for payment " + refund.getPaymentId(),
                currency,
                List.of(
                        new EntryRequest(merchAcctNum, EntryType.DEBIT, refund.getAmount(), currency),
                        new EntryRequest(custAcctNum, EntryType.CREDIT, refund.getAmount(), currency)
                )
        );

        ledgerService.postJournal(journalCommand);
    }
}

