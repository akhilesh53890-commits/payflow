-- ==============================================================================
-- PayFlow Flyway Migration V1: Initial Enterprise Financial Schema
-- PostgreSQL & H2 ANSI SQL compatible
-- ==============================================================================

-- 1. Users & Authentication
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 2. Merchants
CREATE TABLE merchants (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    business_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    sandbox_mode BOOLEAN NOT NULL DEFAULT TRUE,
    webhook_url VARCHAR(512),
    webhook_secret VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_merchants_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 3. API Keys
CREATE TABLE api_keys (
    id VARCHAR(36) PRIMARY KEY,
    merchant_id VARCHAR(36) NOT NULL,
    public_key VARCHAR(64) NOT NULL UNIQUE,
    hashed_secret VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_apikeys_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id)
);

CREATE INDEX idx_apikeys_public ON api_keys(public_key);
CREATE INDEX idx_apikeys_merchant ON api_keys(merchant_id);

-- 4. Refresh Tokens
CREATE TABLE refresh_tokens (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 5. Payments
CREATE TABLE payments (
    id VARCHAR(36) PRIMARY KEY,
    merchant_id VARCHAR(36) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    customer_email VARCHAR(255),
    description VARCHAR(512),
    payment_method VARCHAR(64),
    provider_used VARCHAR(64),
    provider_reference VARCHAR(128),
    failure_reason VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payments_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
    CONSTRAINT chk_payment_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_payments_merchant ON payments(merchant_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_created ON payments(created_at);

-- 6. Payment Attempts
CREATE TABLE payment_attempts (
    id VARCHAR(36) PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    request_payload TEXT,
    response_payload TEXT,
    http_status INTEGER,
    error_message VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_attempts_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_attempts_payment ON payment_attempts(payment_id);

-- 7. Payment State Transitions
CREATE TABLE payment_state_transitions (
    id VARCHAR(36) PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL,
    from_state VARCHAR(32),
    to_state VARCHAR(32) NOT NULL,
    reason VARCHAR(255),
    triggered_by VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_transitions_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_transitions_payment ON payment_state_transitions(payment_id);

-- 8. Durable Idempotency Records
CREATE TABLE idempotency_records (
    id VARCHAR(36) PRIMARY KEY,
    merchant_id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    response_code INTEGER,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_idempotency_merchant_key UNIQUE (merchant_id, idempotency_key),
    CONSTRAINT fk_idempotency_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id)
);

CREATE INDEX idx_idempotency_lookup ON idempotency_records(merchant_id, idempotency_key);

-- 9. Double-Entry Ledger Accounts
CREATE TABLE ledger_accounts (
    id VARCHAR(36) PRIMARY KEY,
    merchant_id VARCHAR(36),
    account_number VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_accounts_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id)
);

CREATE INDEX idx_accounts_merchant ON ledger_accounts(merchant_id);
CREATE INDEX idx_accounts_type ON ledger_accounts(account_type);

-- 10. Ledger Transactions (Journals)
CREATE TABLE ledger_transactions (
    id VARCHAR(36) PRIMARY KEY,
    reference_type VARCHAR(32) NOT NULL,
    reference_id VARCHAR(36) NOT NULL,
    description VARCHAR(255) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ledger_tx_ref ON ledger_transactions(reference_type, reference_id);

-- 11. Ledger Entries (Debits & Credits)
CREATE TABLE ledger_entries (
    id VARCHAR(36) PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(36) NOT NULL,
    entry_type VARCHAR(16) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_entries_tx FOREIGN KEY (transaction_id) REFERENCES ledger_transactions(id),
    CONSTRAINT fk_entries_account FOREIGN KEY (account_id) REFERENCES ledger_accounts(id),
    CONSTRAINT chk_entry_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_entries_tx ON ledger_entries(transaction_id);
CREATE INDEX idx_entries_account ON ledger_entries(account_id);

-- 12. Refunds
CREATE TABLE refunds (
    id VARCHAR(36) PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL,
    merchant_id VARCHAR(36) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reason VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    provider_refund_id VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_refunds_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
    CONSTRAINT chk_refund_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_refunds_payment ON refunds(payment_id);
CREATE INDEX idx_refunds_merchant ON refunds(merchant_id);

-- 13. Risk Assessments
CREATE TABLE risk_assessments (
    id VARCHAR(36) PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL,
    merchant_id VARCHAR(36) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    score INTEGER NOT NULL,
    reasons TEXT,
    rule_versions VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_risk_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_risk_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id)
);

CREATE INDEX idx_risk_payment ON risk_assessments(payment_id);

-- 14. Reconciliation Runs
CREATE TABLE reconciliation_runs (
    id VARCHAR(36) PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    records_analyzed INTEGER NOT NULL DEFAULT 0,
    discrepancies_found INTEGER NOT NULL DEFAULT 0,
    summary TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

-- 15. Reconciliation Discrepancies
CREATE TABLE reconciliation_discrepancies (
    id VARCHAR(36) PRIMARY KEY,
    reconciliation_run_id VARCHAR(36) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id VARCHAR(36) NOT NULL,
    discrepancy_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    details TEXT,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_discrepancies_run FOREIGN KEY (reconciliation_run_id) REFERENCES reconciliation_runs(id)
);

CREATE INDEX idx_discrepancies_run ON reconciliation_discrepancies(reconciliation_run_id);

-- 16. Transactional Outbox
CREATE TABLE outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);

-- 17. Consumer Inbox (Deduplication)
CREATE TABLE consumer_inbox (
    id VARCHAR(36) PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_consumer_event UNIQUE (consumer_name, event_id)
);

-- 18. Webhook Endpoints
CREATE TABLE webhook_endpoints (
    id VARCHAR(36) PRIMARY KEY,
    merchant_id VARCHAR(36) NOT NULL,
    url VARCHAR(512) NOT NULL,
    secret VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_webhooks_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id)
);

CREATE INDEX idx_webhooks_merchant ON webhook_endpoints(merchant_id);

-- 19. Webhook Deliveries
CREATE TABLE webhook_deliveries (
    id VARCHAR(36) PRIMARY KEY,
    webhook_endpoint_id VARCHAR(36) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    signature VARCHAR(256) NOT NULL,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    http_status INTEGER,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_deliveries_endpoint FOREIGN KEY (webhook_endpoint_id) REFERENCES webhook_endpoints(id)
);

CREATE INDEX idx_deliveries_status ON webhook_deliveries(status);
CREATE INDEX idx_deliveries_endpoint ON webhook_deliveries(webhook_endpoint_id);

-- 20. Security & Financial Audit Logs
CREATE TABLE audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    actor_id VARCHAR(128) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(128) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    ip_address VARCHAR(64),
    correlation_id VARCHAR(128),
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_created ON audit_logs(created_at);

