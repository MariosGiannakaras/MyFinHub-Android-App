# Android native-first implementation and consolidated final validation — 2026-10-30

Owner directive: MyFinHub Android already exists. The work should fix defects, make required functions reliable and mobile-friendly, and improve appearance; **do not replicate the Web UI**. Web is authoritative for canonical money/domain representation, server revision rules, privacy, session/vault and security only.

## Execution

- Accumulate related functionality, UI polish, source guards and useful regression coverage on a single active draft feature branch.
- Review the full code batch and do narrow source/static checks while editing. Run **one full final set** of mandatory Project Tracking, Android CI, screenshot regression and hosted Galaxy S24 emulator checks **per coherent feature batch**. Corrections to failed final checks remain on the same PR and must pass all four on the new final head. This retains branch protection and finance-data safety while avoiding a PR/CI run for every small guard or test assertion.
- End-user priorities: functional finance entry, cards and statements, clear mobile navigation, accessible 48dp touch targets, masked balances, working sync/offline behavior and final owner S24 physical acceptance. A Web-only desktop feature is not a requirement by itself.
- Do not publish APK, mutate real financial data, enable shared global PIN, touch backend/Web, or claim owner acceptance because an emulator passed. SMS import #146 remains excluded.

## Batch: Card Detail statement list (Draft PR #405)

- Shared canonical raw statement ID multiplicity from *all source records*, including malformed/archived foreign rows, guards Card Detail expand/payment navigation.
- Mobile-native chips **Όλες** and **Πληρωτέες (count)**. No false zero classification: nonfinite imported balance appears only under All with existing invalid amount warning. The canonical source remains untouched and paid history is recoverable with one tap.
- Financial actions have at least 48dp touch targets. Filtering resets paging/expanded rows to avoid stale mobile navigation. Hide Amounts still masks values.
- Kotlin selection regression plus Compose UI filter and identity checks; actual full final-head CI status is recorded on PR, not assumed.

Existing progress remains **1/6 accepted implementation groups · 11/24 completed subtasks**. Physical owner Galaxy S24 Ultra #149 not inferred from CI.
