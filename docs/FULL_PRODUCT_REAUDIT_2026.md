# Full Product Re-audit — Desktop/Backend → Android

**Date:** 2026-09-07  
**Tracker:** #73  
**Status:** active implementation reference

## Scope

This document deliberately re-evaluates the Android product from first principles against the current canonical desktop product and backend. It does not assume that the existing Android navigation, dashboard composition, or previous redesign passes are the right product structure.

The goal is not to rewrite working infrastructure. Existing canonical finance parsing, repository/offline queue, auth/session/PIN/biometric, card-secret security, updater, signing, and reconciliation boundaries stay in place unless a verified product requirement exposes a concrete gap.

## Canonical desktop capability map

The desktop currently exposes these product areas:

| Desktop area | Canonical user capability | Android ownership after re-audit |
| --- | --- | --- |
| Overview | balances, current cash position, recent movements, upcoming obligations, month summary | Home |
| Transactions | search/filter/read/edit/delete movements | Activity |
| Review | confirm/keep/snooze legacy suggestions and split a movement | Activity → Review |
| Savings | savings balance/progress and three canonical saving-source actions | Money → Savings |
| Cards | debit/prepaid vault management by bank | Money → Cards |
| Credit card | credit debt/limit, purchases, payments, statements, card lifecycle | Money → Credit |
| Loans & installments | active/completed obligations, payment, edit, self-loan support | Money → Debt |
| Lending/receivables | people, outstanding receivables, lend/repay history | Money → Receivables |
| Recurring | recurring obligations/subscriptions, lifecycle and payment | Plan → Recurring |
| Planning | one-off scheduled items, completion, lifecycle, deterministic forecast | Plan |
| Attention | urgent/warning/info signals with action/snooze/dismiss | Home → Attention |
| Reports | trends, comparisons, categories, obligations, account series, budget context | Insights |
| Settings | general preferences, accounts, budgets/goals, taxonomy, data/backup/import, diagnostics | Settings |

The mobile product should preserve all supported capabilities, but it should not port thirteen desktop pages or desktop dashboard geometry into thirteen mobile destinations.

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

Therefore Android navigation must be organized around **user goals**, not backend tables and not desktop routes.

## Problems found in the current Android IA

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

The desktop has separate pages for Review, Attention, Reports, Recurring, Savings, Cards, Credit, Loans and Lending. A direct mobile copy would create too many first-level destinations. These must be grouped under the five stable mobile goals.

## New mobile information architecture

### Bottom navigation

1. **Αρχική** — the accounts that matter most now.
2. **Κινήσεις** — find, inspect and correct financial events.
3. **Περιουσία** — accounts, savings, cards, debt and receivables.
4. **Πλάνο** — scheduled, recurring, budgets and deterministic forecast.
5. **Εικόνα** — comparisons, trends, category concentration and reports.

Settings remains a secondary app-level destination, not a sixth bottom-navigation item.

### Secondary ownership

- **Activity** owns Review because both workflows correct/confirm movement semantics.
- **Money** owns Savings, Cards/Credit, Loans and Lending.
- **Plan** owns Recurring and budgets because they describe future obligations.
- **Home** owns Attention because it answers “what needs action now?” without becoming another finance-summary dashboard.
- **Insights** owns report-only derived analysis and must not repeat Home or Activity.

## Content ownership rules

These rules are mandatory for the redesign:

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

## Production screen redesign

### Home

Primary task:
- see the important accounts immediately, without first interpreting a global total.

Keep / implement:
- the same three primary account identities used by the desktop dashboard: `cash`, `piraeus-payroll`, `piraeus-savings`;
- canonical current balance per primary account;
- compact seven-day canonical balance trend per primary account;
- secondary accounts below the primary three;
- fast entry access;
- attention/upcoming content only when it remains concise and action-oriented.

Do not use as the lead content:
- total available/liquid money;
- net position;
- monthly income/expense KPI hero;
- savings/budget aggregate hero.

Account metadata such as IBAN is a separate backend contract (`/api/account-metadata`). Android must not fabricate it from FinanceData; it can be added only through an explicit safe API integration.

### Activity

Primary task:
- browse and work with actual transactions.

Keep / implement:
- search;
- account filter;
- chronological date/month sections;
- transaction rows;
- detail/edit/delete where canonically supported;
- new-transaction entry.

Do not show:
- global or filtered finance hero;
- income/expense/net KPI summary;
- transaction-type chips solely for dashboard-style segmentation.

“Needs review” remains a secondary Activity workflow rather than another main destination.

### Money → “Περιουσία”

Primary task:
- inspect durable assets, liabilities and financial instruments that do not belong in the compact Home prioritization.

Sections:
- accounts/details beyond Home’s primary three;
- savings;
- cards/credit;
- debt;
- receivables.

Avoid repeating Home’s three account cards or decorative top-level totals when the same values are immediately repeated in sections below.

### Plan

Primary task:
- what is due and what happens next.

Sections:
- due now;
- upcoming one-off items;
- recurring;
- expected income/transfers;
- budget controls;
- deterministic forecast.

Show forecast once.

### Insights → “Εικόνα”

Primary questions:
- Is spending improving or worsening?
- What changed from the previous month?
- Where is spending concentrated?
- What is the savings trend?

Do not show another current-month net-flow hero.

### Settings

Group by:
- Appearance & privacy
- Finance defaults
- Accounts/taxonomy
- Data, backup/history
- Diagnostics/update/session

Do not surface disabled desktop-only tabs as if they were supported mobile functionality.

## Functional parity gaps to close after the IA pass

The current canonical Android projection already covers accounts, movements, savings aggregate, cards, debt aggregate, receivables aggregate, scheduled/recurring projection, budget, forecast and basic insights. The following desktop capabilities require deliberate Android access checks rather than visual imitation:

- legacy Review suggestions and split confirmation;
- complete Attention aggregation beyond overdue scheduled expenses;
- recurring lifecycle/payment actions;
- richer credit statement lifecycle and purchases/payments where safely supported;
- backup/import/history access at Settings level;
- account metadata management through the separate owner+AAL2 metadata contract;
- report comparisons beyond the minimal current Android insight projection.

Each gap must reuse canonical server/domain semantics. If the current Android model cannot express a desktop action safely, the UI must not invent a local-only approximation.

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
