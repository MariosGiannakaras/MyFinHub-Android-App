# Web ↔ Android reconciliation — canonical execution plan

Owner approval: 2026-10-14. Product reference: current `MariosGiannakaras/MyFinHub/develop`. Android target: `MyFinHub-Android-App/develop`. Work issue: #155, current draft PR: #156. This file defines **stable scope only**. Changing status, blockers, exact PR and next action live exclusively in `tracking/android-project-state.json → reconciliation_workstream`; the generated STATUS, TODO and CURRENT_HANDOFF must match.

## Owner-locked decisions

1. Home leading total includes only semantic daily cash and payroll; excludes reserves, savings, other banks and other available balances
2. Three web-semantic primary accounts each have a chart, followed by simple secondary account rows
3. Cards and credit use one web-parity PaymentCard identity and integrated credit workspace

## Six implementations / 24 tracked subtasks

### W1: Canonical accounts and Home

**Web/backend reference:** Web domain.ts, accountSelection.ts, accountSemantics.ts, DashboardPage.tsx, dashboardAccounts.ts.

**W1.1 Canonical account merge and roles** — Account overrides, custom accounts, banking/cash metadata, provider IDs and account eligibility match Web.

**W1.2 Home total and three primary account slots** — Hero is exclusively daily cash plus payroll; three slots Cash, Payroll, Savings with charts, then compact remaining accounts; no other bank or reserve in total.

**W1.3 Chart/target/IBAN web-fidelity** — Use Web-accurate history and comparative savings charts, 30-day deltas, goal, IBAN/provider identity, period and privacy states.

**W1.4 Home tests and shared projections** — Web↔Android fixture parity, accessible Compose/S24 real screenshots, Wallet/Quick Entry consistent account projections, exact-head CI.

### W2: Card identity and integrated credit experience

**Web/backend reference:** Web types.ts, cardDesigns.ts, CardsPage.tsx, CreditCardPage.tsx, InteractivePaymentCard.tsx.

**W2.1 Canonical card metadata/lifecycle** — Read designId, formFactor, bankId, card limit, billing-cycle and historical/archived references, with single card identity source.

**W2.2 Shared native card visual** — Use explicit Web card design and current provider assets across all Android surfaces, never randomized card designs.

**W2.3 Credit information on card screen** — Below the credit card place amount/limit/utilization, statements, purchases, movements and repayments without compulsory detail page.

**W2.4 Card interactions and secure details** — In-context purchase/payment, safe archive/restore/delete, card picker, secure vault only on protected view, navigation and screenshot tests.

### W3: Events, statements and mutation semantics

**Web/backend reference:** Web domain.ts, creditStatements.ts, cards.ts, transactionRules.ts, App.tsx.

**W3.1 Statement cycle and historical debt** — Statement open/closed/due/paid, allocation, legacy card ID ownership and accurate card debt parity.

**W3.2 Event and classification parity** — Canonical legs, transfers, refunds, savings, splits, lending, loans, reconciliation, date/money validation, category rules.

**W3.3 Card repayment provider validation** — Match eligible source by canonical bank/provider metadata, not string-prefix guesses, with card debt limits.

**W3.4 Single mutation pipeline** — Unify Android event builders and shared validation, preserve durable offline/revision semantics; test with Web-derived vectors.

### W4: Recurring, forecast, savings and reporting

**Web/backend reference:** Web recurring.ts, recurringCadence.ts, forecast.ts, SavingsPage.tsx, PlanningPage.tsx, ReportsPage.tsx.

**W4.1 Recurring lifecycle** — Include custom/overridden recurring entries, cadence, paid history, pause/stop, next due and service metadata.

**W4.2 Scheduled forecast** — Web-equivalent 30/60/90-day projections, loan/recurring/scheduled impacts, low-balance warnings, no double count.

**W4.3 Savings, loans, lending and budgets** — Goals, targets, savings offset eligibility, correct debts/receivables/installments, overall and category budgets.

**W4.4 Transactions and analytics completeness** — Consistent history, filters, categories/drilldowns, edit/delete, comparisons and remaining Web domain flows.

### W5: Providers, API, security and settings

**Web/backend reference:** Web financialProviders.ts, accountMetadataHandler.ts, deviceSessionsHandler.ts, accountSecurityHandler.ts, useFinance.ts.

**W5.1 Dynamic provider/IBAN/assets contract** — Read canonical providers, metadata, IBAN, logos/wordmarks/card marks and remove hardcoded bank assumptions.

**W5.2 Auth/sync/history consistency** — AAL2/owner bearer, optimistic revisions, history generation, pending/offline NEEDS_REVIEW, no blind retries.

**W5.3 Card vault and security endpoints** — Preserve secure PAN/expiry/CVV and lifecycle; inspect device/account security endpoints; document native-API blocker if needed.

**W5.4 Settings, backup, rules and ancillary parity** — Reconcile settings, backup/change history, receipt OCR where supported and approved, provider/service assets; exclude SMS import.

### W6: Parity validation, cleanup and device acceptance

**Web/backend reference:** Android unit/Compose/UI screenshot checks and matching Web pure-domain fixtures; issue #149 remains separate.

**W6.1 Golden cross-client fixture tests** — Assert account/credit/recurring/savings/categorization and legacy fixture outcomes and revision semantics.

**W6.2 Real Compose accessibility/visual verification** — S24 light/dark, large text, privacy, screen-reader/touch, account charts/card visuals/credit workspace.

**W6.3 Exact-head CI, deduplication and tracking handoff** — Targeted then broader checks, only remove replaced code, update tracker/docs in coherent merged checkpoints.

**W6.4 Owner physical acceptance and release isolation** — Record real S24 approval without inferring from CI; no new signed publication without release authorization.

## Exact completion and resumption rules

- Always report **Web parity X/6 implementations · Y/24 subtasks** from evidence-backed completed statuses in the canonical JSON; phase counts from all four subtasks. Do not count source-only draft PR progress as completed. Historical redesign 10/10 and 40/40 stays separate.
- Resume from `AGENTS.md`, this plan, the canonical state, generated handoff, permanent issue #27, issue #155, live Android/Web develop and relevant PR/checks. Inspect only direct paths and dependencies, never infer current state from old chats or restart completed audits.
- Each verified coherent checkpoint should update the canonical JSON, regenerate docs, run targeted/broader checks appropriately, commit and push, and merge to Android develop only when gates pass. Completed tasks must have test/merge/visual evidence; blocked tasks need explicit cause. Keep current_subtask and next_action accurate so another chat/agent can continue immediately.
- Web FinanceData, Supabase/private ledger, owner+AAL2, security/RLS, optimistic revision/history, encrypted offline pending intent, server card vault and release signing are invariants. Kotlin/Jetpack Compose is native; no mechanical desktop cloning, WebView, competing Android DB or unapproved backend API edits.
- No new production release or physical acceptance may be assumed. Existing rc12 physical follow-up #149 is independent. SMS/notification import #146 excluded. No real finance data/secrets committed.

## Current handoff at plan creation

Branch `android/web-parity-home-20261014`, unmerged draft PR #156: first account/domain and Home source is committed but lacks verified exact-head Kotlin/Compose tests, real-render screenshots, shared card system and complete reconciliation. W1.1 and W1.2 are in_progress; all other subtasks pending. Read live tracking for fresher evidence.
