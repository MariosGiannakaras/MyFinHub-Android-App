# Android card detail / Wallet financial safety parity — 2026-10-29

W2.4/W3.1 Android-only, native Compose source checkpoint, issue #155.

## Why

The inline Wallet credit workspace already rejects nonfinite debt/credit limits and masks invalid history amounts. The separate card-detail route previously formatted imported NaN/Infinity with NumberFormat and independently enabled card payment for positive infinity, rather than using the same canonical credit financial snapshot. Its archive confirmation also incorrectly said reversible archive deleted encrypted card details, contradicting the previously validated owner+AAL2 marker-only **permanent deletion** cleanup boundary (#183/#184/#187).

## Implementation

- Reuse `creditWorkspaceFinancialSnapshot` and `creditWorkspaceOptionalMoney` in credit card details. Debt, limit and available credit match inline Wallet. Reject all nonfinite imported debt, omit unsupported limit, show explicit error only when amounts are visible; never fabricate a zero balance or allow repayment for an invalid debt.
- Shared `creditWorkspaceSignedMoney` formats finite signed history; NaN/Infinity remains an invalid source state while Hide Amounts masks both normal and invalid values. Preserve navigation to the canonical activity ID and normal positive sign.
- Correct **reversible archive** confirmation: finance history/debt and encrypted vault data are retained. Viewing secrets after restore still requires authorized owner+AAL2. This changes description only; permanent deletion and vault cleanup remain disabled/unmodified.
- Compose regressions cover valid payment, invalid debt/limit/activity, Hide Amounts, archive confirmation, and proper selected-card archive callback. Pure Kotlin numeric/formatting edge cases augment existing credit financial snapshot tests. A test-only `amountsVisibleOverride` defaulted nullable does not change production preferences.

No Web/backend/API/schema changes, financial mutations, global shared PIN (issue #345), permanent deletion UI, SMS import (#146), signing, Android main or release. Physical S24 owner acceptance #149 is independent. Broad parity counters remain 1/6 implementations and 11/24 subtasks while W2.4 and W3.1 are incomplete.
