package com.payflow.ledger.service;

import com.payflow.common.MoneyUtils;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.exception.ValidationException;
import com.payflow.ledger.domain.*;
import com.payflow.ledger.dto.LedgerDtos.*;
import com.payflow.ledger.repository.LedgerAccountRepository;
import com.payflow.ledger.repository.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerAccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;

    public LedgerService(
            LedgerAccountRepository accountRepository,
            LedgerTransactionRepository transactionRepository,
            LedgerEntryRepository entryRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional
    public LedgerAccount getOrCreateAccount(String merchantId, String accountNumber, String name, AccountType type, String currency) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseGet(() -> {
                    LedgerAccount newAccount = new LedgerAccount(merchantId, accountNumber, name, type, currency);
                    return accountRepository.save(newAccount);
                });
    }

    @Transactional
    public LedgerTransactionDto postJournal(PostJournalCommand command) {
        if (command.entries() == null || command.entries().size() < 2) {
            throw new ValidationException("Double-entry journal requires at least two balanced entries.");
        }

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        Map<String, LedgerAccount> accountCache = new HashMap<>();

        for (EntryRequest req : command.entries()) {
            if (!MoneyUtils.isPositive(req.amount())) {
                throw new ValidationException("Ledger entry amount must be strictly positive: " + req.amount());
            }
            if (req.currency() != null && !req.currency().equalsIgnoreCase(command.currency())) {
                throw new ValidationException("Entry currency " + req.currency() + " does not match transaction currency " + command.currency());
            }

            LedgerAccount account = accountRepository.findByAccountNumber(req.accountNumber())
                    .orElseThrow(() -> new ResourceNotFoundException("Ledger account not found: " + req.accountNumber()));
            accountCache.put(req.accountNumber(), account);

            if (req.entryType() == EntryType.DEBIT) {
                totalDebits = totalDebits.add(req.amount());
            } else if (req.entryType() == EntryType.CREDIT) {
                totalCredits = totalCredits.add(req.amount());
            }
        }

        // Mathematical Financial Invariant: Debits MUST equal Credits
        totalDebits = MoneyUtils.format(totalDebits);
        totalCredits = MoneyUtils.format(totalCredits);

        if (totalDebits.compareTo(totalCredits) != 0) {
            log.error("Ledger imbalance rejected for refId={}: debits={}, credits={}, diff={}",
                    command.referenceId(), totalDebits, totalCredits, totalDebits.subtract(totalCredits));
            throw new IllegalStateException("Ledger journal is unbalanced! Debits (" + totalDebits +
                    ") must equal Credits (" + totalCredits + ") per currency " + command.currency());
        }

        // Persist transaction header
        LedgerTransaction tx = new LedgerTransaction(
                command.referenceType(),
                command.referenceId(),
                command.description(),
                command.currency()
        );
        LedgerTransaction savedTx = transactionRepository.save(tx);

        List<LedgerEntryDto> entryDtos = new ArrayList<>();
        for (EntryRequest req : command.entries()) {
            LedgerAccount account = accountCache.get(req.accountNumber());
            LedgerEntry entry = new LedgerEntry(
                    savedTx.getId(),
                    account.getId(),
                    req.entryType(),
                    MoneyUtils.format(req.amount()),
                    command.currency()
            );
            LedgerEntry savedEntry = entryRepository.save(entry);
            entryDtos.add(new LedgerEntryDto(
                    savedEntry.getId(),
                    savedTx.getId(),
                    account.getId(),
                    account.getAccountNumber(),
                    savedEntry.getEntryType(),
                    savedEntry.getAmount(),
                    savedEntry.getCurrency(),
                    savedEntry.getCreatedAt()
            ));
        }

        log.info("Posted balanced journal txId={} refType={} refId={} balancedAmount={}",
                savedTx.getId(), savedTx.getReferenceType(), savedTx.getReferenceId(), totalDebits);

        return new LedgerTransactionDto(
                savedTx.getId(),
                savedTx.getReferenceType(),
                savedTx.getReferenceId(),
                savedTx.getDescription(),
                savedTx.getCurrency(),
                savedTx.getCreatedAt(),
                entryDtos
        );
    }

    @Transactional
    public LedgerTransactionDto reverseJournal(String originalTxId, String reason) {
        LedgerTransaction originalTx = transactionRepository.findById(originalTxId)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger transaction not found: " + originalTxId));

        List<LedgerEntry> originalEntries = entryRepository.findByTransactionId(originalTxId);
        if (originalEntries.isEmpty()) {
            throw new IllegalStateException("Original transaction has no entries to reverse: " + originalTxId);
        }

        LedgerTransaction reversalTx = new LedgerTransaction(
                ReferenceType.ADJUSTMENT,
                originalTx.getReferenceId(),
                "Reversal of " + originalTx.getId() + ": " + reason,
                originalTx.getCurrency()
        );
        LedgerTransaction savedReversalTx = transactionRepository.save(reversalTx);

        List<LedgerEntryDto> reversalDtos = new ArrayList<>();
        for (LedgerEntry orig : originalEntries) {
            // Mirror entry with inverted entry type (DEBIT -> CREDIT, CREDIT -> DEBIT)
            LedgerEntry reversalEntry = new LedgerEntry(
                    savedReversalTx.getId(),
                    orig.getAccountId(),
                    orig.getEntryType().invert(),
                    orig.getAmount(),
                    orig.getCurrency()
            );
            LedgerEntry savedReversalEntry = entryRepository.save(reversalEntry);

            LedgerAccount account = accountRepository.findById(orig.getAccountId()).orElse(null);
            String acctNum = account != null ? account.getAccountNumber() : "UNKNOWN";

            reversalDtos.add(new LedgerEntryDto(
                    savedReversalEntry.getId(),
                    savedReversalTx.getId(),
                    orig.getAccountId(),
                    acctNum,
                    savedReversalEntry.getEntryType(),
                    savedReversalEntry.getAmount(),
                    savedReversalEntry.getCurrency(),
                    savedReversalEntry.getCreatedAt()
            ));
        }

        log.info("Successfully posted reversal journal txId={} for originalTxId={}", savedReversalTx.getId(), originalTxId);
        return new LedgerTransactionDto(
                savedReversalTx.getId(),
                savedReversalTx.getReferenceType(),
                savedReversalTx.getReferenceId(),
                savedReversalTx.getDescription(),
                savedReversalTx.getCurrency(),
                savedReversalTx.getCreatedAt(),
                reversalDtos
        );
    }

    @Transactional(readOnly = true)
    public AccountBalanceDto getAccountBalance(String accountNumber) {
        LedgerAccount account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        BigDecimal debits = entryRepository.sumDebitsByAccountId(account.getId());
        BigDecimal credits = entryRepository.sumCreditsByAccountId(account.getId());

        debits = MoneyUtils.format(debits);
        credits = MoneyUtils.format(credits);

        // Standard accounting equation:
        // Asset & Expense: Net = Debits - Credits
        // Liability, Equity, Revenue: Net = Credits - Debits
        BigDecimal netBalance;
        if (account.getAccountType() == AccountType.ASSET || account.getAccountType() == AccountType.EXPENSE) {
            netBalance = debits.subtract(credits);
        } else {
            netBalance = credits.subtract(debits);
        }

        return new AccountBalanceDto(
                account.getAccountNumber(),
                account.getName(),
                account.getAccountType(),
                account.getCurrency(),
                debits,
                credits,
                netBalance
        );
    }

    @Transactional(readOnly = true)
    public List<LedgerAccountDto> listAccounts(String merchantId) {
        List<LedgerAccount> accounts;
        if (merchantId != null) {
            accounts = accountRepository.findByMerchantId(merchantId);
        } else {
            accounts = accountRepository.findAll();
        }
        return accounts.stream()
                .map(a -> new LedgerAccountDto(
                        a.getId(), a.getMerchantId(), a.getAccountNumber(), a.getName(),
                        a.getAccountType(), a.getCurrency(), a.getStatus(), a.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<LedgerTransactionDto> listTransactions(Pageable pageable) {
        return transactionRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(tx -> {
                    List<LedgerEntryDto> entries = entryRepository.findByTransactionId(tx.getId()).stream()
                            .map(e -> {
                                LedgerAccount acct = accountRepository.findById(e.getAccountId()).orElse(null);
                                String num = acct != null ? acct.getAccountNumber() : "UNKNOWN";
                                return new LedgerEntryDto(
                                        e.getId(), e.getTransactionId(), e.getAccountId(), num,
                                        e.getEntryType(), e.getAmount(), e.getCurrency(), e.getCreatedAt());
                            }).toList();
                    return new LedgerTransactionDto(
                            tx.getId(), tx.getReferenceType(), tx.getReferenceId(), tx.getDescription(),
                            tx.getCurrency(), tx.getCreatedAt(), entries);
                });
    }
}

