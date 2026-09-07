# Android Product Re-Audit

This document records the active Android product re-audit against the current canonical MyFinHub desktop product and backend. It is intentionally product-level: stable auth, security, revision, offline/reconciliation, updater and production-signing infrastructure remain in place unless a verified product dependency requires a change.

## Mobile information ownership

- **Αρχική** — account-first landing surface. It prioritizes the same three primary account identities used by the desktop dashboard (`cash`, `piraeus-payroll`, `piraeus-savings`), shows each account's current canonical balance and a compact canonical seven-day balance trend, then lists secondary accounts below. It does not lead with a total-money aggregate.
- **Κινήσεις** — transaction workspace only. It owns search, account filtering, chronological transaction rows, detail/edit/delete where supported, and entry into a new transaction. It does not own an aggregate finance hero or transaction-type summary chips.
- **Περιουσία** — durable assets/liabilities workspace: accounts beyond the Home prioritization, cards, savings, loans and lending/receivables.
- **Πλάνο** — forward-looking obligations, expected income, transfers and budget/forecast workflows.
- **Εικόνα** — analytical interpretation and insights; it must not duplicate the account landing surface or transaction list.
- **Quick Entry** — focused canonical mutation flow for creating supported finance events.
- **Settings** — preferences, security, updater and app controls rather than finance-summary content.

## Duplication rules

1. A metric should have one primary home. Other screens may link to it but should not repeat a full hero/summary solely for decoration.
2. Home answers “which accounts matter now?” rather than “what is my total net position?”.
3. Activity answers “what happened?” and therefore remains a transaction list with search/account filtering.
4. Money answers “what do I own/owe?”, Plan answers “what comes next?”, and Insights answers “what does the data mean?”.
5. Mobile surfaces may derive read-only presentation values from the canonical FinanceData contract, but must not invent writable semantics unsupported by the backend.

## Current visual-validation checkpoint

Fresh real Compose candidate renders for the account-first Home and transaction-only Activity were personally inspected. The first candidate exposed two large-font defects: the Home section action could be pushed off-screen, and the Activity entry action could cover the final visible transaction. The Home hierarchy/action fix passed the next visual inspection. Extra list padding and viewport reserve were both rejected after follow-up 150% renders still showed the Activity action over list content. Activity now uses a real Scaffold bottom action backed by an opaque screen-colored Surface, so list content cannot remain visibly behind the action. The re-audit also removed the stale hidden expense filter from Insights-to-Activity navigation; opening Activity from Insights now returns to the transaction workspace without an invisible filter the user cannot clear, and the action label is correspondingly neutral.

The final exact-head real Compose renders were personally inspected in light, dark and large-font states across Home, Activity, Money, Plan, Insights, Quick Entry and Settings. The opaque Activity action is clean at 150% font, no remaining clipping or overlap was found in the reviewed production surfaces, and the resulting screenshot references have been accepted as the canonical re-audit baselines. The remaining hosted checkpoint is therefore a clean exact-head screenshot-regression pass with no candidate regeneration, representative S24-target instrumentation, Project Tracking verification and full Android CI/R8. Only after those gates are green and review state is clean may PR #83 merge to `develop`; `main` remains out of scope and physical Samsung Galaxy S24 Ultra owner acceptance remains authoritative after a higher protected same-signer production candidate is published.
