# Full Product Re-audit — Desktop/Backend → Android

**Date:** 2026-09-07  
**Tracker:** #73  
**Status:** hosted validation in progress

## Scope

This document deliberately re-evaluates the Android product from first principles against the current canonical desktop product and backend. It does not assume that the existing Android navigation, dashboard composition, or previous redesign passes are the right product structure.

The goal is not to rewrite working infrastructure. Existing canonical finance parsing, repository/offline queue, auth/session/PIN/biometric, card-secret security, updater, signing, and reconciliation boundaries stay in place unless a verified product requirement exposes a concrete gap.

## Canonical desktop capability map

| Desktop area | Canonical user capability | Android ownership after re-audit |
| --- | --- | --- |
| Overview | balances, current cash position, recent movements, upcoming obligations, month summary | Home |
| Transactions | search/filter/read/edit/delete movements | Activity |
| Review | confirm/keep/snooze legacy suggestions and split a movement | Activity → Review follow-up |
| Savings | savings balance/progress and canonical saving-source actions | Money → Savings |
| Cards | debit/prepaid vault management by bank | Money → Cards |
| Credit card | credit debt/limit, purchases, payments, statements, card lifecycle | Money → Credit follow-up where mutation semantics are not yet wired |
| Loans & installments | active/completed obligations, payment, edit, self-loan support | Money → Debt; lifecycle mutations remain follow-up where not canonically wired |
| Lending/receivables | people, outstanding receivables, lend/repay history | Money → Receivables |
| Recurring | recurring obligations/subscriptions, lifecycle and payment | Plan → Recurring follow-up where mutation semantics are not yet wired |
| Planning | one-off scheduled items, completion, lifecycle, deterministic forecast | Plan |
| Attention | urgent/warning/info signals with action/snooze/dismiss | Home → Attention |
| Reports | trends, comparisons, categories, obligations, account series, budget context | Insights |
| Settings | general preferences, accounts, budgets/goals, taxonomy, data/backup/import, diagnostics | Settings; server history/backup/import remain follow-up until real APIs are integrated |

The mobile product preserves safely supported capabilities without porting thirteen desktop pages or desktop dashboard geometry into thirteen mobile destinations.

## Backend contract map

The product backend is substantially simpler than the desktop page count implies:

- `/api/auth/*` owns password login, MFA/TOTP, owner-only session state and logout.
- Finance reads require an owner session at **AAL2**.
- `/api/data` is the canonical revisioned finance document.
- Finance writes use optimistic concurrency (`If-Match`) plus history generation and validated mutable state.
- `/api/history` owns cross-session undo/redo.
- `/api/account-metadata` owns account metadata such as IBAN separately from the finance JSON.
- `/api/card-secrets` owns payment-card secrets separately from finance state.
- `/api/import` replaces the document only with explicit confirmation and validation.
- `/api/backup` creates a server-side backup.
- Android release metadata is separate from finance state.

Supabase reinforces those boundaries:

- `rheomiq_app_state`: singleton revisioned JSON state.
- `rheomiq_backups`: backup snapshots.
- `rheomiq_owner`: single authorized owner.
- `rheomiq_audit_log`: save/import/backup/undo/redo audit.
- `rheomiq_history_points` + `rheomiq_history_cursor`: immutable recovery points and optimistic history cursor.
- `rheomiq_account_metadata`: owner/account metadata.
- `rheomiq_card_secrets`: ciphertext-only card secret storage; plaintext PAN/expiry/CVV must not be persisted there.
- `rheomiq_financial_providers` + assets: stable provider identity and owner-provided visual assets.
- `rheomiq_android_releases`: private signed Android release metadata.

Therefore Android navigation is organized around **user goals**, not backend tables and not desktop routes.

## Problems found in the prior Android IA

### Repeated information

The prior production surfaces repeated the same values in multiple top-level contexts:

- monthly income/expense appeared on Home, Activity and Insights;
- savings appeared on Home, Money and Insights;
- budget appeared on Home and Plan;
- account totals appeared on Home and Money;
- forecast/end balance appeared twice inside Plan;
- “hero” treatment was used on nearly every top-level tab, so every screen competed to be the dashboard.

This made the application feel like several variations of the same summary instead of distinct tools.

### Weak ownership

Some information did not have a single canonical screen owner:

- Home mixed stock values, flow values, budget and savings.
- Activity began with another finance summary instead of the movement-management task.
- Money mixed portfolio totals with the same account/savings values already shown on Home.
- Plan repeated its forecast and used budget as both summary content and a configuration destination.
- Insights began with current-month net flow again instead of answering “what changed and why?”.

### Desktop-shaped secondary functionality

The desktop has separate pages for Review, Attention, Reports, Recurring, Savings, Cards, Credit, Loans and Lending. A direct mobile copy would create too many first-level destinations. These are grouped under five stable mobile goals, while unsupported mutations remain explicit follow-up work rather than fake local controls.

## New mobile information architecture

### Bottom navigation

1. **Αρχική** — the accounts that matter most now.
2. **Κινήσεις** — find, inspect and correct financial events.
3. **Περιουσία** — accounts, savings, cards, debt and receivables.
4. **Πλάνο** — scheduled, recurring, budgets and deterministic forecast.
5. **Εικόνα** — comparisons, trends, category concentration and reports.

Settings remains a secondary app-level destination, not a sixth bottom-navigation item.

### Secondary ownership

- **Activity** owns movement correction and is the future home of Review semantics.
- **Money** owns Savings, Cards/Credit, Loans and Lending.
- **Plan** owns Recurring and budgets because they describe future obligations.
- **Home** owns Attention because it answers “what needs action now?” without becoming another finance-summary dashboard.
- **Insights** owns report-only derived analysis and must not repeat Home or Activity.

## Content ownership rules

1. A financial metric has one primary home.
2. Other screens may reference it only when it is needed to complete that screen’s task.
3. No top-level screen gets a decorative hero merely for visual consistency.
4. Home is account-first: it prioritizes the three canonical primary accounts and then secondary accounts; it does not lead with total cash or net-position aggregation.
5. Activity starts with search/account filtering/results and contains transactions only; it does not own global month KPIs or transaction-type summary chips.
6. Money owns current stock categories beyond the Home prioritization: balances, debts, receivables and card position.
7. Plan owns future state: due items, recurring obligations, budget controls and forecast.
8. Insights owns change over time, comparisons and concentration; it does not restate current totals.
9. Sensitive card values are revealed only in card detail under the existing secure-window/session boundaries.
10. Every canonical workflow should be reachable within two meaningful taps from a top-level destination where feasible.
11. A desktop action is exposed on Android only when the current Android API/domain layer can express the canonical mutation and recovery semantics safely.

## Production screen redesign

### Home

Primary task: see the important accounts immediately, without first interpreting a global total.

Implemented:
- the same three primary account identities used by the desktop dashboard: `cash`, `piraeus-payroll`, `piraeus-savings`;
- canonical current balance per primary account;
- compact seven-day canonical balance trend per primary account;
- secondary accounts below the primary three;
- fast entry access;
- concise attention/upcoming content.

Not used as lead content:
- total available/liquid money;
- net position;
- monthly income/expense KPI hero;
- savings/budget aggregate hero.

Account metadata such as IBAN is a separate backend contract (`/api/account-metadata`). Android does not fabricate it from FinanceData; management remains a follow-up until that owner+AAL2 API is explicitly integrated.

### Activity

Primary task: browse and work with actual transactions.

Implemented:
- search;
- account filter;
- chronological date/month sections;
- transaction rows;
- detail/edit/delete where canonically supported;
- new-transaction entry in a real opaque Scaffold bottom action so large-font rows cannot remain visible behind it.

Removed:
- global or filtered finance hero;
- income/expense/net KPI summary;
- transaction-type chips used as dashboard segmentation;
- hidden Insights-to-Activity expense filtering that had no visible Activity control to clear it.

“Needs review” remains a secondary Activity follow-up rather than another main destination.

### Money → “Περιουσία”

Primary task: inspect durable assets, liabilities and financial instruments that do not belong in the compact Home prioritization.

Sections:
- accounts/details beyond Home’s primary three;
- savings;
- cards/credit;
- debt;
- receivables.

The screen keeps one net-position overview and avoids repeating the same four values again inside the hero before their detailed sections.

### Plan

Primary task: what is due and what happens next.

Implemented sections:
- upcoming obligations;
- expected income;
- scheduled transfers;
- canonical overall budget controls;
- deterministic forecast shown once.

Recurring/payment/lifecycle write controls are not fabricated where current canonical mutation semantics are not wired.

### Insights → “Εικόνα”

Primary questions:
- Is spending improving or worsening?
- What changed from the previous month?
- Where is spending concentrated?
- What is the savings trend?

The screen does not show another current-month net-flow hero. Its Activity action opens the transaction workspace neutrally; it does not silently apply a filter that the user cannot see or clear.

### Quick Entry

The production quick-entry fast path was re-reviewed. Expense, income and transfer remain optimized around amount-first entry and canonical synchronized account/category choices. Less-frequent supported finance types continue into the complete canonical editor. No new local-only mutation semantics were introduced.

### Settings

Production Settings exposes real controls only:
- appearance and amount visibility;
- privacy/card-secret explanation and privacy-safe notice history;
- updater;
- logout/session control;
- diagnostics.

The existing `ChangeHistoryScreen`/`FrontendUtilitiesUiState.history` is synthetic preview/test state and is not exposed as real production server history. Backup/import/history and other desktop data-management actions remain follow-up work until the corresponding canonical API operations are explicitly integrated.

## Explicit follow-up parity gaps

These are not blockers to the information-architecture redesign because Android would otherwise have to invent semantics not backed by its current canonical API/domain layer:

- legacy Review suggestion confirmation/keep/snooze and split workflows;
- complete Attention aggregation beyond currently projected canonical signals;
- recurring lifecycle/payment mutations;
- richer credit statement/purchase/payment lifecycle;
- loan/lending lifecycle mutations not already represented safely by the Android domain layer;
- real server `/api/history`, `/api/backup` and `/api/import` controls;
- `/api/account-metadata` management such as IBAN;
- richer report comparisons beyond the current read-only insight projection.

Each follow-up must reuse canonical server/domain semantics, optimistic concurrency and existing ambiguity/reconciliation rules. No local-only approximation is accepted as parity.

## Visual-validation result

Fresh exact-head real Compose candidate renders were personally inspected for the redesigned production surfaces in light, dark and large-font states. During inspection:
- Home’s large-font section action was moved into a stable vertical hierarchy;
- Activity’s first padding-only and viewport-reserve fixes were rejected because the action still obscured or visually overlaid list content at 150% font;
- Activity was converted to a real Scaffold bottom action and then given an opaque screen-colored Surface;
- the final Activity light/dark/150% renders show no transaction content behind the action;
- Home, Money, Plan, Insights, Quick Entry and Settings showed no remaining clipping or overlap in the reviewed variants.

The inspected candidate references are approved for baseline replacement. A clean exact-head screenshot-regression pass, representative S24-target instrumentation and full Android CI/R8 remain required before merge.

## Validation contract

For every changed production surface:
- render real Compose screenshots;
- inspect light mode;
- inspect dark mode;
- inspect large-font mode;
- correct clipping, overlap, hierarchy or contrast defects before baseline acceptance;
- update canonical screenshot references only after visual inspection;
- run Project Tracking, screenshot regression, representative S24-target instrumentation and full Android CI/R8;
- publish only through the protected same-signer production pipeline;
- final acceptance remains physical Samsung Galaxy S24 Ultra owner acceptance.
