# D3 credit Quick Entry route integrity (native Android) — 2026-10-30

## Product defect and fix
Quick Entry buttons in the existing Wallet and Card Detail could open with a card ID remembered from an earlier Compose frame even if canonical state had since deleted/archived that credit card or changed its card kind or outstanding balance. The final mutation layer still validates source, but the mobile navigation/prefill should not lead to a dead-end or offer misleading repayment.

- Added `canUseCreditCardActions` and `canOpenCreditCardQuickEntry`: require a nonblank active card identity, non-archived source row, and **canonical** credit kind. Legacy `kind` label is used only when canonicalKind is blank; an explicit debit identity must never be overridden by a stale localized “Πιστωτική” label. Payment additionally needs a finite positive current credit debt; purchase can be offered at zero debt.
- Both UI surfaces use this same credit-kind truth; all **six** native actions (Wallet pay card/pay selected statement/add purchase, Card Detail pay card/pay selected statement/add purchase) reread current production card state immediately before initializing `QuickEntryAction`. Statement routes retain separate source-global ID, owner and stale amount checks. No new transaction is posted during navigation.
- Kotlin regressions cover missing/card deleted/archived, canonical debit label conflict, malformed/zero balance, valid purchase and legitimate unlabeled legacy card. Hosted Compose test confirms a mislabeled canonical debit never renders credit payment controls; screenshot fixtures otherwise unchanged.
- This repairs native financial usability/safety and does not clone the Web. No backend/schema/Web, live finance write, SMS #146, shared native PIN #345, physical installation, release signing.

Progress: **deploy 2/8 accepted**; D3 Cards/Credit/Quick Entry and D4 Activity still open pending end-to-end acceptance. W1–W6 historical bookkeeping stays 1/6 implementation groups, 11/24 subtasks. All four final-head CI gates required before merge; no device acceptance inferred from hosted emulator.
