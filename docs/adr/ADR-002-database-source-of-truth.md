# ADR-002: PostgreSQL as the Financial Source of Truth

## Status
Accepted

## Context
Financial platforms demand strict consistency, durability, auditability, and mathematical correctness. Redis is frequently used in web architectures for caching, session storage, and distributed locks, but Redis does not provide the ACID transaction guarantees, durable relational integrity, constraints, or multi-statement rollback required for financial systems.

## Decision
1. **PostgreSQL as Sole Authority**:
   - PostgreSQL is the authoritative system of record for all business and financial entities: Accounts, Balances, Ledger Entries, Payments, Idempotency Keys, Refunds, and Outbox logs.
   - All balance updates and state changes occur inside serialized or `READ COMMITTED` PostgreSQL transactions with pessimistic row locking (`SELECT FOR UPDATE`) or optimistic versioning where appropriate.
   - Database constraints (foreign keys, `CHECK` constraints, unique indexes) strictly enforce financial invariants at the schema level.
2. **Redis as Optimization Only**:
   - Redis may be used for transient caching (e.g., API key validation cache, rate limiting, provider health status cache).
   - Redis is **never** relied upon for financial ledger state, payment state transitions, or idempotency durability.
   - If Redis becomes unavailable, the platform continues to operate correctly with graceful degradation in latency, falling back directly to PostgreSQL.

## Consequences
- **Positive**: Zero risk of phantom financial entries or lost ledger balances due to Redis eviction, snapshot intervals, or network partitions.
- **Positive**: Database constraints prevent data corruption even if application-level bugs occur.
- **Trade-off**: Requires thoughtful relational indexing, connection pooling (HikariCP), and query optimization to handle high throughput.

