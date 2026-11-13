# ADR-006: Provider Orchestration and Ambiguous Timeout Resolution

## Status
Accepted

## Context
When orchestrating payments against third-party payment gateways (Stripe, Adyen, banking rails), network requests can time out, connection drops can occur, or gateways can return 504 Gateway Timeout / 500 Internal Server Error without a conclusive payment status.

A naive or reckless implementation might:
1. Blindly retry the payment against the same provider (risking double-charging the customer).
2. Blindly fail over to a secondary provider (charging the customer twice if the first provider actually captured the funds).
3. Treat a timeout as a definitive `FAILED` payment.

## Decision
1. **Clear Outcome Categorization**:
   - Provider responses are normalized into three distinct outcome classes:
     - `DEFINITIVE_SUCCESS`: Payment authorized / captured. Transition state to `SUCCEEDED`.
     - `DEFINITIVE_DECLINE`: Card declined, insufficient funds, invalid CVV. Transition state to `FAILED`. Safe to allow merchant/user to try another card or provider.
     - `AMBIGUOUS_OUTCOME`: Socket timeout, HTTP 500/502/503/504, connection reset, or read timeout after dispatch.
2. **Strict Ambiguity Handling**:
   - Never blindly retry or fail over to another provider when an ambiguous timeout occurs.
   - The payment state transitions to **`UNKNOWN`** (or `PROCESSING` flagged for investigation).
   - An audit and outbox event is emitted for investigation.
3. **Active Resolution Protocol**:
   - A reconciliation / status inquiry worker performs an explicit provider query (`provider.inquirePaymentStatus(providerReference)`) using the correlation ID.
   - If the provider confirms the transaction was processed, the payment transitions to `SUCCEEDED` and the double-entry ledger journal is posted atomically.
   - If the provider confirms no transaction exists or was rejected, the payment transitions to `FAILED`.
   - If the status cannot be resolved after configured attempts, the payment remains in `UNKNOWN` requiring human / ops reconciliation.

## Consequences
- **Positive**: Strict prevention of double-charging and rogue duplicate card authorizations.
- **Positive**: Explicit operational visibility into ambiguous states.
- **Trade-off**: Requires a state polling or webhook callback reconciliation loop for unresolved transactions.

