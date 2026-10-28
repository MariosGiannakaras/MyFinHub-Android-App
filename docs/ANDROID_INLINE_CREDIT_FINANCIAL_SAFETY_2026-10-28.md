# Inline credit card financial display — source integrity guard (28 Oct 2026)

The owner requires a single debit/credit card stack, with debt, limit, statements and movements rendered directly beneath the active credit card. Financial source integrity takes precedence over rendering a plausible default.

- Card balances are only clamped to a minimum of zero **after finite-source validation**. Imported NaN/Infinity debt displays "Μη έγκυρο ποσό" when amounts are visible, never false €0.00; payment action is disabled.
- Card limit must be positive and finite; missing limit remains absent, but a present invalid limit explicitly warns instead of painting ∞/NaN. Available credit and utilization are displayed only for valid finite arithmetic; percentage/progress bars never receive NaN/Infinity.
- Statement outstanding/purchases/payments and card-linked movement amounts individually show a finite-source warning rather than invalid formatted currency. Canonical row order and action identity are preserved.
- Existing amount-hide privacy remains authoritative: invalid financial source values still show masked symbols, not their source values or warning details; no payment action can execute with an invalid source debt.
- Tests include pure Double.NaN, positive/negative infinity and ratio overflow boundaries, valid refund/over-limit behavior, and hosted S24 Compose malformed-source and privacy assertions.

Scope: Android read-only projection/presentation safety; no Web/backend/schema, ledger mutation, card vault, SMS import or release/signing changes.
