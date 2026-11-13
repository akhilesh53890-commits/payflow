# ADR-001: Modular Monolith Architecture

## Status
Accepted

## Context
PayFlow is a high-reliability fintech payment orchestration and financial infrastructure platform. The platform must model several distinct domains: Identity & Access, Merchant Management, Payment Processing, Idempotency, Provider Orchestration, Double-Entry Ledger, Refunds, Risk Engine, Reconciliation, Webhooks, Audit, and Eventing.

A naive approach to complex systems is jumping directly to microservices. However, distributed transactions across microservices introduce the dual-write problem, partial failure modes, network latency, operational overhead, and distributed consensus complexity (2PC/Sagas) which can jeopardize financial ledger consistency.

## Decision
We choose a **Modular Monolith** architecture for PayFlow initially:
1. **Single Spring Boot Deployment**: One runtime artifact and deployment unit, simplifying operational maintenance, distributed tracing, and local developer setup.
2. **Package-by-Feature Domain Isolation**: Package root `com.payflow` with strictly isolated modules:
   - `identity`
   - `merchant`
   - `payment`
   - `idempotency`
   - `provider`
   - `ledger`
   - `refund`
   - `risk`
   - `reconciliation`
   - `webhook`
   - `audit`
   - `eventing`
   - `common`
3. **Enforced Dependency Boundaries**:
   - No module may directly inject or query another module's Spring Data JPA repositories or mutate another module's entities.
   - Cross-module interaction happens strictly through public Java interfaces (e.g., `LedgerService`, `PaymentProvider`, `RiskEngine`) or domain events published through Spring's ApplicationEventPublisher and Transactional Outbox.
4. **Single Financial Database**: PostgreSQL acts as the single transactional source of truth, enabling atomic database transactions encompassing payment state transitions, idempotency records, ledger entries, and outbox event records.

## Consequences
- **Positive**: Atomic consistency across payment transitions, ledger records, and outbox logs using standard ACID database transactions. Zero risk of distributed dual-write inconsistency between ledger and payments.
- **Positive**: Low deployment complexity, ultra-fast test execution using Testcontainers, and simple debugging.
- **Positive**: Clear module boundaries allow any module (e.g., Ledger or Webhook engine) to be extracted into an independent microservice in the future if scale warrants.
- **Trade-off**: Requires strict developer discipline and architectural tests (e.g., ArchUnit-style package enforcement) to prevent forbidden cross-package dependencies.

