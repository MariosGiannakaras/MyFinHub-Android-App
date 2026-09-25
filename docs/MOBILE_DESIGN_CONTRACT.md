# MyFinHub Android mobile design contract

Status: Phase 0 initial contract  
Date: 2026-08-22  
Depends on: `RESEARCH_MOBILE_UX_2026.md`

## Objective

The Android application must provide the complete MyFinHub capability set through a native mobile information architecture. It may reorganize presentation and interaction, but it must not silently remove, merge, reinterpret, or change finance behavior.

The contract distinguishes **product parity** from **layout parity**:

- Product parity is required.
- Desktop/web layout parity is not required and is often undesirable.
- Brand identity, terminology, finance semantics, and state remain recognizable across clients.
- Android conventions take precedence where the web interaction is mouse/keyboard/large-screen-specific.

## Primary compact information architecture

The first prototype should validate a five-destination model, which is within Android's 3-5 destination guidance:

1. **Home** — financial position, actionable attention, upcoming items, Quick Entry, key trends.
2. **Activity** — transactions, review workflow, global transaction filtering/search, recent changes relevant to finance activity.
3. **Money** — savings, bank/account/card/credit surfaces, loans and lending/receivables.
4. **Plan** — recurring obligations, scheduled transactions, budgets, rules, cash-flow forecast and planning tools.
5. **Insights** — reports/analytics and longer-horizon financial interpretation.

Settings is accessed from profile/app chrome rather than consuming a bottom-navigation destination. Global search is reachable from Home/Activity and may later become more globally persistent if prototype testing shows value.

This five-destination model is a prototype hypothesis, not a license to force every feature into an unnatural parent. If prototype evidence shows four destinations produce a clearer mental model, revise through an ADR before implementation spreads.

## Larger-window navigation

The same destination model adapts to a navigation rail on medium/expanded widths. Do not maintain a second tablet-specific navigation tree.

List-heavy domains use adaptive list-detail presentation when width permits:

- Activity transaction list + transaction detail/edit context;
- Cards list + card detail/security controls;
- Loans list + loan detail/payments;
- Recurring list + schedule detail;
- Rules/budgets list + editor/supporting context.

## Screen contracts

### Home

Purpose: answer "Where do I stand, what needs attention, and what can I do next?"

Compact order should preserve MyFinHub's established semantic hierarchy:

1. primary liquid balances / current financial position;
2. savings and other important balances;
3. actionable Needs Attention/upcoming obligations when present;
4. Quick Entry;
5. key trends/analytics;
6. lower-priority summaries and navigation cards.

Rules:

- Do not create a tile wall containing every desktop widget.
- Keep the most decision-relevant amount/action visible without horizontal scrolling.
- Each summary section links to its owning destination/detail.
- Empty sections collapse cleanly and explain the next meaningful action where needed.

### Activity

Purpose: answer "What happened, what is pending/reviewable, and how do I find an event?"

Includes:

- transactions;
- pending/scheduled activity where contextually relevant;
- Smart Review/Review entry and status;
- search/filter/sort;
- transaction detail/edit;
- split/transfer/payment semantics through their proper editor flows.

Transaction rows must be optimized for scanning. Default compact row information should prioritize amount, type/category, account/card context when meaningful, and date/state. Free-text notes are not used as the primary scanning label if doing so creates privacy/noise problems.

Bulk actions enter an explicit selection mode. Permanent dense action buttons on every row are discouraged.

### Money

Purpose: answer "What financial containers, debts, cards, and receivables do I have?"

Initial children:

- Savings;
- Cards;
- Credit;
- Loans/installments;
- Lending/receivables;
- relevant account/balance management surfaces.

Cards and Credit must continue to share the same underlying `PaymentCard` identity model. Android presentation may use card imagery and focused detail screens, but must not create a duplicate card domain.

Sensitive card behavior:

- PAN/expiry reveal/edit remains server-vault-backed.
- CVV remains device-local only.
- Secure details are not displayed in overview lists, previews, screenshots/goldens, recents thumbnails, or accessibility descriptions.

### Plan

Purpose: answer "What future commitments and rules shape my money?"

Includes:

- scheduled transactions;
- recurring obligations;
- budgets;
- deterministic transaction rules;
- 30/60/90-day cash-flow forecasting;
- planning workflows.

Prefer grouped summary lists and focused editors. Do not attempt to reproduce a desktop planning grid on compact screens.

### Insights

Purpose: answer "What does my financial history/current trajectory mean?"

Includes current Reports/Analytics capability.

Compact principles:

- one primary question per chart/section;
- timeframe/metric changes through compact controls;
- ranked category lists and KPI summaries accompany charts where they improve interpretation;
- charts never rely on color alone;
- every visual has a meaningful accessible text/data summary;
- drill-down is preferred over a dense dashboard of tiny charts.

### Settings

Access through profile/app chrome. Settings is a list of grouped configuration sections with dedicated child screens for complex settings. Keyboard shortcut references from desktop/web should not be copied blindly; Android-specific shortcuts are shown only where supported and useful on hardware keyboards.

## Quick Entry contract

Quick Entry remains a first-class capability.

Initial compact behavior:

- one primary add/Quick Entry action can use a FAB on Home and Activity where it does not obscure content;
- the same editor is reusable from context-specific entry points;
- relevant context can prefill account/card/category/type without changing the underlying transaction model;
- Android app shortcuts/widget entry may be added later, calling the exact same domain flow.

Transaction type must be explicit when it changes accounting semantics (expense, income, transfer, withdrawal, saving offset, refund, lending, repayment, card purchase/payment, reconciliation, split).

## Tables and list conversion rules

For each desktop table, define a **mobile row contract** instead of a generic column-preservation rule.

A compact row should expose only values needed for recognition/comparison. Details move to a child screen or expandable supporting content. Horizontal scrolling is reserved for specialized comparison cases and is not the default implementation.

Each row contract must specify:

- primary identifier;
- primary amount/status;
- one or two secondary metadata fields;
- accessibility label/order;
- row tap destination;
- contextual actions;
- empty/loading/error states.

## Forms

All forms must have a clear mobile task order.

- Vertically ordered fields/groups on compact screens.
- Relevant fields appear based on transaction/action type; irrelevant fields are hidden rather than creating a disabled desktop matrix.
- Correct keyboard type/IME action for amounts, dates, labels, numeric secrets, etc.
- Focus and scrolling keep the active field and submit action visible with the IME open.
- Inline validation for field-specific errors; submission summary for multi-field blocking errors when necessary.
- State survives rotation/window resizing/process recreation where reasonable.
- Save/confirm is visually separated from archive/delete/permanent-secret deletion.

## Sheets, menus, dialogs, screens

Decision rule:

- **Overflow menu:** a few simple contextual actions.
- **Bottom sheet:** bounded choices, filters, pickers, or short secondary action flows.
- **Dialog:** concise confirmation/decision requiring interruption.
- **Dedicated destination:** complex edit, secure card management, multi-step transaction/payment, full report/drill-down.

Do not put a long multi-section finance form in a sheet merely to avoid navigation.

## Search

Global search should eventually cover privacy-safe entities that users reasonably expect to find across the product. Results are grouped by entity type and take the user to the native owning screen.

Transaction filtering remains available within Activity even if global search expands.

Search results and indexing must not surface PAN, expiry, CVV, vault references, authentication data, or other secrets.

## Touch and gesture contract

- Minimum interactive target: 48dp x 48dp.
- Long press and swipe are accelerators, never the only path to an essential action.
- Destructive swipe behavior requires confirmation/undo semantics appropriate to the operation and must have a visible alternative.
- Predictive Back must work through normal navigation/sheet/dialog hierarchy.
- Pull-to-refresh is used only when users can meaningfully request fresh server state; avoid cargo-cult refresh on purely local views.
- Haptics may confirm high-confidence local interactions but must not be the only feedback.

## Loading, empty, error, conflict states

The Android client must preserve the same seriousness as the web app around stale state and security failures.

Required states include:

- first load/auth loading;
- route/feature loading skeleton appropriate to the compact layout;
- empty state with next action when useful;
- offline/network-unavailable state;
- auth expired / MFA required;
- optimistic revision conflict with explicit reload/reconcile behavior;
- server validation error;
- secure vault unavailable/misconfigured state without leaking secrets.

Do not silently overwrite local edits after a revision conflict.

## Accessibility contract

Every feature PR must satisfy applicable items:

- 48dp targets;
- meaningful semantics/roles/states;
- correct TalkBack traversal order;
- dynamic font scaling without clipping/overlap;
- contrast targets;
- no color-only finance state;
- accessible alternative to gestures;
- charts provide semantic/text summaries;
- modal/sheet pane semantics are correct;
- content descriptions never expose secret card values.

## Privacy-by-design UI rules

- Do not include real data in Compose previews or screenshot golden files.
- Use synthetic fixtures with obviously fictional names/amounts.
- Sensitive card values should use `FLAG_SECURE`/secure-window behavior on reveal flows where technically appropriate; this must be evaluated during secure-card prototype work.
- Avoid writing financial values to logs, crash breadcrumbs, analytics, or test diagnostics.
- Android recent-app previews must be reviewed for sensitive screens before release.

## Prototype gates before full implementation

Create representative prototypes/tests for these flows before scaling the design system across all features:

1. Home on compact and expanded windows.
2. Activity transaction list -> detail/edit -> back, including search/filter.
3. Quick Entry for expense, transfer, card payment, and split.
4. Cards list -> secure card detail/reveal flow.
5. Plan list -> complex editor.
6. Insights chart + accessible summary + drill-down.
7. A medium/expanded list-detail example.
8. Large-font/TalkBack semantics pass on the representative screens.

The final information architecture should be confirmed after these prototypes, not by copying the existing web route tree.