# ADR-005: Transactional Outbox Pattern for Asynchronous Events

## Status
Accepted

## Context
Payment and financial state changes must be published to external listeners, webhooks, audit subsystems, and analytics via Apache Kafka. Writing directly to Kafka inside the database transaction creates the dual-write problem: if the database commits but Kafka is down, events are lost; if Kafka accepts the event but the database rolls back, phantom events are emitted. Furthermore, distributed transactions (XA / 2PC) across PostgreSQL and Kafka introduce latency, failure cascades, and high operational overhead.

## Decision
1. **Transactional Outbox Table**:
   - Every domain event (`PaymentCreated`, `PaymentSucceeded`, `PaymentFailed`, `RefundInitiated`, `RefundCompleted`, `LedgerJournalPosted`) is inserted into the `outbox_events` table **in the exact same database transaction** as the entity state mutation.
   - If the business transaction rolls back, no outbox event is persisted.
2. **Asynchronous Outbox Publisher**:
   - A scheduled background worker polls the `outbox_events` table for `PENDING` records, ordered by `created_at ASC`.
   - Records are published to Apache Kafka with a key derived from the entity ID (e.g., `paymentId` or `merchantId`) to guarantee partition-level ordering.
   - Upon successful Kafka acknowledgment, the record status is updated to `PUBLISHED` with `published_at` timestamp.
   - In case of Kafka downtime or broker unavailability, the outbox publisher records errors, applies exponential backoff, and leaves records in `PENDING` status.
3. **Delivery Guarantees (At-Least-Once Delivery)**:
   - We explicitly acknowledge that Kafka delivery provides **at-least-once** guarantees. We do not claim distributed exactly-once delivery across external systems.
4. **Idempotent Consumers (Inbox Pattern)**:
   - Event consumers maintain a deduplication `consumer_inbox` table storing `(consumer_name, event_id)` with a unique constraint.
   - If a duplicate event is received, it is discarded idempotently without re-triggering business effects.

## Consequences
- **Positive**: Zero event loss even during full Kafka cluster outages or broker failovers.
- **Positive**: Complete prevention of phantom events from aborted database transactions.
- **Trade-off**: Events arrive asynchronously with slight propagation latency (sub-second polling interval).

