package com.payflow.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "merchant_id", length = 36)
    private String merchantId;

    @Column(name = "account_number", nullable = false, unique = true, length = 64)
    private String accountNumber;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    private AccountType accountType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public LedgerAccount() {}

    public LedgerAccount(String merchantId, String accountNumber, String name, AccountType accountType, String currency) {
        this.id = UUID.randomUUID().toString();
        this.merchantId = merchantId;
        this.accountNumber = accountNumber;
        this.name = name;
        this.accountType = accountType;
        this.currency = currency;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getMerchantId() { return merchantId; }
    public String getAccountNumber() { return accountNumber; }
    public String getName() { return name; }
    public AccountType getAccountType() { return accountType; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}

