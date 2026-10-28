# Android credit statement source guards — 2026-10-28

Owner-approved, Android-only W3.1 statement presentation integrity batch within issue #155.

- Both the inline Wallet credit workspace and historical statement section independently filter imported canonical statement rows using **exact** selected `card.id === statement.cardId`, including mixed-card and blank-identity fail-closed cases. Source ordering and duplicate IDs stay visible for integrity warnings rather than silently rewritten.
- Historical statement repayment actions require a unique nonblank statement ID, a same-card statement and **finite positive** remaining amount. No mutation, payment amount or callback can originate from a mismatched or NaN/Infinity imported statement.
- Nonfinite historical statement amounts display an explicit Greek invalid-amount state; Hide Amounts masks them without disclosing the invalid source value. No guessed dates, statement totals, new API endpoints or cross-client contract changes.
- Kotlin unit and hosted Compose regressions cover mixed-card isolation, preserved in-card row order, invalid amounts, blocked repayment, privacy and existing valid repayment behavior.
- This is not full W2.4/W3.1 parity completion; physical S24 owner acceptance #149 and shared global PIN native ticket #345 remain separate. No Web/backend/schema, live finance mutation, signing, main, release or SMS import.
