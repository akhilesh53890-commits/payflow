package com.payflow.ledger.dto;

import com.payflow.ledger.domain.AccountType;
import com.payflow.ledger.domain.EntryType;
import com.payflow.ledger.domain.ReferenceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class LedgerDtos {

    public record EntryRequest(
            String accountNumber,
            EntryType entryType,
            BigDecimal amount,
            String currency
    ) {}

    public record PostJournalCommand(
            ReferenceType referenceType,
            String referenceId,
            String description,
            String currency,
            List<EntryRequest> entries
    ) {}

    public record LedgerAccountDto(
            String id,
            String merchantId,
            String accountNumber,
            String name,
            AccountType accountType,
            String currency,
            String status,
            Instant createdAt
    ) {}

    public record LedgerEntryDto(
            String id,
            String transactionId,
            String accountId,
            String accountNumber,
            EntryType entryType,
            BigDecimal amount,
            String currency,
            Instant createdAt
    ) {}

    public record LedgerTransactionDto(
            String id,
            ReferenceType referenceType,
            String referenceId,
            String description,
            String currency,
            Instant createdAt,
            List<LedgerEntryDto> entries
    ) {}

    public record AccountBalanceDto(
            String accountNumber,
            String accountName,
            AccountType accountType,
            String currency,
            BigDecimal debitTotal,
            BigDecimal creditTotal,
            BigDecimal netBalance
    ) {}
}

