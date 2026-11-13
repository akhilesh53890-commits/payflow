package com.payflow.reconciliation.service;

import com.payflow.common.MoneyUtils;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.ledger.domain.LedgerEntry;
import com.payflow.ledger.domain.LedgerTransaction;
import com.payflow.ledger.domain.ReferenceType;
import com.payflow.ledger.repository.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerTransactionRepository;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentState;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.reconciliation.domain.ReconciliationDiscrepancy;
import com.payflow.reconciliation.domain.ReconciliationRun;
import com.payflow.reconciliation.repository.ReconciliationDiscrepancyRepository;
import com.payflow.reconciliation.repository.ReconciliationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationDiscrepancyRepository discrepancyRepository;
    private final PaymentRepository paymentRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public ReconciliationService(
            ReconciliationRunRepository runRepository,
            ReconciliationDiscrepancyRepository discrepancyRepository,
            PaymentRepository paymentRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository
    ) {
        this.runRepository = runRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.paymentRepository = paymentRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public record RunDetailDto(
            ReconciliationRun run,
            List<ReconciliationDiscrepancy> discrepancies
    ) {}

    @Transactional
    public RunDetailDto executeReconciliation() {
        log.info("Starting automated reconciliation run...");
        ReconciliationRun run = new ReconciliationRun("IN_PROGRESS");
        ReconciliationRun savedRun = runRepository.save(run);

        List<ReconciliationDiscrepancy> discrepancies = new ArrayList<>();
        List<Payment> allPayments = paymentRepository.findAll();
        int recordsAnalyzed = allPayments.size();

        for (Payment payment : allPayments) {
            if (payment.getStatus() == PaymentState.SUCCEEDED) {
                // Check if Ledger Transaction exists
                List<LedgerTransaction> txs = ledgerTransactionRepository.findByReferenceTypeAndReferenceId(
                        ReferenceType.PAYMENT, payment.getId());

                if (txs.isEmpty()) {
                    ReconciliationDiscrepancy disc = new ReconciliationDiscrepancy(
                            savedRun.getId(), "PAYMENT", payment.getId(),
                            "MISSING_LEDGER", "CRITICAL",
                            "Succeeded payment " + payment.getId() + " has no corresponding double-entry ledger journal."
                    );
                    discrepancies.add(discrepancyRepository.save(disc));
                } else {
                    // Check balanced ledger amount
                    LedgerTransaction tx = txs.get(0);
                    List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(tx.getId());
                    BigDecimal debitSum = entries.stream()
                            .filter(e -> e.getEntryType().name().equals("DEBIT"))
                            .map(LedgerEntry::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    if (MoneyUtils.format(debitSum).compareTo(payment.getAmount()) != 0) {
                        ReconciliationDiscrepancy disc = new ReconciliationDiscrepancy(
                                savedRun.getId(), "PAYMENT", payment.getId(),
                                "AMOUNT_MISMATCH", "HIGH",
                                "Payment amount " + payment.getAmount() + " does not match ledger debit amount " + debitSum
                        );
                        discrepancies.add(discrepancyRepository.save(disc));
                    }
                }
            } else if (payment.getStatus() == PaymentState.UNKNOWN) {
                ReconciliationDiscrepancy disc = new ReconciliationDiscrepancy(
                        savedRun.getId(), "PAYMENT", payment.getId(),
                        "UNRESOLVED_TIMEOUT", "MEDIUM",
                        "Payment remains in UNKNOWN state pending provider status inquiry."
                );
                discrepancies.add(discrepancyRepository.save(disc));
            }
        }

        savedRun.setStatus("COMPLETED");
        savedRun.setRecordsAnalyzed(recordsAnalyzed);
        savedRun.setDiscrepanciesFound(discrepancies.size());
        savedRun.setCompletedAt(Instant.now());
        savedRun.setSummary("Reconciliation complete. Analyzed " + recordsAnalyzed +
                " records, found " + discrepancies.size() + " discrepancies.");
        runRepository.save(savedRun);

        log.info("Reconciliation run {} finished: {} discrepancies found", savedRun.getId(), discrepancies.size());
        return new RunDetailDto(savedRun, discrepancies);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationRun> listRuns(Pageable pageable) {
        return runRepository.findAllByOrderByStartedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public RunDetailDto getRunDetails(String runId) {
        ReconciliationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation run not found: " + runId));
        List<ReconciliationDiscrepancy> discrepancies = discrepancyRepository.findByReconciliationRunIdOrderByCreatedAtAsc(runId);
        return new RunDetailDto(run, discrepancies);
    }
}

