# ADR-004: Immutable Double-Entry Ledger Architecture

## Status
Accepted

## Context
Payment processing platforms must maintain exact mathematical integrity of all money movements. Single-entry accounting ("update account set balance = balance + 10") is prone to race conditions, silent record loss, lack of provenance, and inability to audit historical states.

## Decision
1. **Double-Entry Journal System**:
   - Every financial transaction consists of a `LedgerTransaction` header and at least two balanced `LedgerEntry` items (one or more DEBIT entries and one or more CREDIT entries).
   - **Balance Invariant**:
     $$\sum \text{Debits} = \sum \text{Credits} \quad \text{per Currency}$$
   - Any transaction where the sum of debits does not equal the sum of credits is rejected with a fatal error and transaction rollback.
2. **Account Types and Representation**:
   - Standard account types: `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE`.
   - Dedicated internal accounts:
     - Merchant Settlement Account (`LIABILITY`)
     - Customer Funds Held (`ASSET`)
     - Payment Processor Transit (`ASSET`)
     - Platform Fee Income (`REVENUE`)
     - Chargeback / Refund Reserve (`LIABILITY`)
3. **Immutability of Posted Entries**:
   - Once a ledger transaction is posted, its records are strictly **immutable**.
   - `UPDATE` and `DELETE` operations on `ledger_entries` and `ledger_transactions` are prohibited by application logic and database triggers / schema permissions.
   - Financial adjustments, partial refunds, and full reversals are recorded as new balancing reversal journal entries referencing the original transaction ID.
4. **Precision**:
   - Monetary amounts are strictly stored as `NUMERIC(19, 4)` in PostgreSQL and represented as `BigDecimal` in Java. Floating-point types (`float`, `double`) are strictly prohibited in financial code.

## Consequences
- **Positive**: Complete audit trail and exact balance reproducibility at any point in time.
- **Positive**: Impossible for financial money to "disappear" without an offsetting balancing entry.
- **Trade-off**: Requires strict validation on all journal creation and reversal logic.

