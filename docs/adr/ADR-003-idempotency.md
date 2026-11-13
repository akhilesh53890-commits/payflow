# ADR-003: Durable PostgreSQL-Enforced Idempotency

## Status
Accepted

## Context
Network timeouts, mobile re-connections, and client retries frequently cause the same payment request to be submitted multiple times. Without robust idempotency mechanisms, clients risk creating duplicate charges, phantom authorizations, and double-spent balances.

## Decision
1. **Durable Database Enforcement**:
   - Every mutating request to `/api/v1/payments` and `/api/v1/refunds` must provide an `Idempotency-Key` HTTP header.
   - Idempotency records are scoped to `(merchant_id, idempotency_key)` and stored in the PostgreSQL `idempotency_records` table with a unique constraint:
     `CONSTRAINT uq_idempotency_merchant_key UNIQUE (merchant_id, idempotency_key)`
2. **Canonical Payload Hashing**:
   - The incoming request payload is canonicalized (normalized JSON keys, trimmed strings) and hashed using SHA-256.
   - When a request arrives:
     - If the `(merchant_id, idempotency_key)` does not exist: An idempotency record is inserted with status `IN_PROGRESS` and the `request_hash`.
     - If the record already exists and status is `COMPLETED`:
       - If the incoming hash matches the stored hash: Return the previously cached HTTP status code and response body (HTTP 200/201).
       - If the incoming hash differs: Return **HTTP 409 Conflict** with an explicit error code `IDEMPOTENCY_KEY_PAYLOAD_MISMATCH`.
     - If the record exists and status is `IN_PROGRESS`: Return **HTTP 409 Conflict** or lock-wait using `SELECT ... FOR UPDATE` with a short timeout to prevent concurrent duplicate execution.
3. **Transaction Safety**:
   - The idempotency record is locked or committed within the database transaction. If the business logic fails with an unhandled exception before reaching a valid business outcome, the transaction rolls back, allowing a safe retry.
   - Once a payment is finalized (e.g., `SUCCEEDED`, `FAILED`, `UNKNOWN`), the idempotency record is updated with `COMPLETED`, the response body, and status code.

## Consequences
- **Positive**: Complete protection against duplicate charges across concurrent threads and distributed clients.
- **Positive**: Strict mismatch detection prevents malicious or accidental reuse of keys for different amounts or destinations.
- **Trade-off**: Requires dedicated table storage and periodic pruning of expired idempotency keys (e.g. 24-48 hours TTL).

