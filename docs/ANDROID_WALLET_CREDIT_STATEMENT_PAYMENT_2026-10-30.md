# Wallet direct statement payment — native Android batch, 2026-10-30

**Goal:** Complete the actual native Wallet credit user journey; do not force users to open Card Detail before paying a specific statement.

- Inline credit workspace uses existing canonical statement list and shares `CardStatementFilter` with Card Detail. For 3+ statements, phone-native Material3 48dp chips `Όλες` and `Πληρωτέες (N)` reduce clutter while leaving settled/invalid historical rows available under All; paging resets on filter change.
- A specific finite, positively payable and **globally source-unique** statement displays a 48dp `Πληρωμή κατάστασης` touch action only when the caller has a real payment handler. No guessed identifiers; production Wallet receives full raw physical statement-ID cardinality. Imported duplicate/colliding and invalid sources are read-only with clear warning.
- Wallet → existing Quick Entry `CARD_PAYMENT` preselects canonical card, statement ID, and current remaining amount. Both the Wallet and Card Detail routes share `canOpenCreditStatementPayment` before prefill; the existing Quick Entry and canonical mutation/replay gates continue independently to validate before any write.
- Reuses native Compose components, does not replicate Web UI; no new backend, schema, release, synthetic transaction, price rewrite, signing, or physical-device claim.
- New hosted Compose and Kotlin regression checks authored. Run a single four-gate final validation on PR #406 head and record actual pass/fail before merge.

## Deploy progress

The canonical tracker now records D1–D8. **2/8** complete (existing Android foundation, native Home/accounts), D3 Cards/Credit/Quick Entry in progress. Do not count an authored PR as D3 complete. D7 physical owner Galaxy S24 Ultra (#149) and D8 signed deploy remain independent and unapproved.

Historical Web parity remains **1/6 implementations · 11/24 subtasks** for continuity, not an app construction percentage.
