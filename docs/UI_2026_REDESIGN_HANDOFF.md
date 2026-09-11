# MyFinHub Android — master redesign specification

## Authority and scope

Owner decision, 2026-09-11: prepare the repository and implement the substantial Android redesign with memory-independent tracking. Android only; Samsung Galaxy S24 Ultra is the physical reference. No changes to MyFinHub web/desktop, shared Supabase schema/data/configuration, APIs or server behavior. Preserve canonical finance semantics, unknown fields, revisions, auth, pending/reconciliation, vault and signing contracts.

This replaces the old completed-redesign handoff (retained in Git history). Current progress lives only in tracking/android-project-state.json; STATUS.md, TODO.md and docs/CURRENT_HANDOFF.md are generated. Issue #73 tracks implementation; #27 bootstraps new chats. Resume the recorded branch/PR and first unfinished subtask. Do not restart discovery.

Audit baseline: develop 449ccb7adc3add2812c89f5bd868760b9c900641. Production paths, projections, ViewModels and representative screenshots were inspected. Inventory: 84 Compose baseline PNGs, 27 screenshots-folder images and four other raster assets. Not every screenshot was individually viewed. No new Android build or physical device test was performed during that audit. rc8 is a technical baseline, not accepted final design.

## Document precedence and cleanup

The following documents remain at their paths as historical evidence, avoiding broken links: MOBILE_DESIGN_CONTRACT, DESIGN_SYSTEM_COMPONENT_SPEC, DESIGN_SYSTEM_PIXEL_SPEC_PLAN, FULL_PRODUCT_REAUDIT_2026, ANDROID_PRODUCT_REAUDIT, RESEARCH_MOBILE_UX_2026, UI_UX_CANONICAL_FINDINGS, UI_UX_CANONICAL_CORRECTION_SCOPE, UI_UX_CANONICAL_IMPLEMENTATION_PLAN, UI_UX_HARDENING_PLAN, RC7_S24_OWNER_UI_UX_CORRECTION_PLAN. Their visual/navigation decisions do not override this specification. Valid security/financial evidence remains relevant. Read only direct dependencies of the active slice.

Preparation changes instructions/specification/tracking, not app behavior. Do not delete source, assets or screenshot baselines speculatively. Remove legacy components only after production routing has a tested replacement and references/previews are accounted for. Keep historical release facts distinct from new redesign completion.

## Product and navigation

Aim: one primary figure, one primary action, flat readable lists and details on demand. Remove repeated totals/provider labels, decorative nested cards and passive summaries. Do not invent bank capabilities: card removal is not bank cancellation; notice history is not a server financial audit log.

Four always-labelled bottom destinations: **Αρχική, Κινήσεις, Πορτοφόλι, Πλάνο**.
- Κινήσεις: Ιστορικό / Ανάλυση. Insights moves here.
- Πορτοφόλι: Λογαριασμοί / Κάρτες / Οφειλές. At 150% font use a labelled section-selector sheet if those labels do not fit.
- Savings becomes an account group; net position a compact link to its own breakdown.
- Settings opens from Home. No bottom quick-action toolbar or competing global FABs.
- Preserve each top-level stack, filters, scroll and selection across tab switches/refresh. Detail Back returns to origin. Hide bottom navigation in editors, secure-card screens and authentication. Predictive/system Back obeys dirty-form rules. Never hide navigation labels at large fonts.

Retained inventory: Home/attention; Activity/search/filter/detail/edit/delete; all twelve Quick Entry kinds; account ledger; card creation/detail/reveal/secret editing/removal; savings/loans/claims; Plan/30-day forecast/overall budget; Insights; settings/privacy/theme; diagnostics/notices/update; login/TOTP/PIN/biometric; loading/empty/offline/pending/review/error.

Production capability limitations: account administration is not wired; card metadata update is not a complete existing mutation; loan/claim item lists are not populated by the current production projection; Plan editing is not production navigation; ChangeHistory is synthetic preview. Verify existing supported operations before exposing controls. If unavailable, preserve useful read summaries and record the exact blocked dependency. Never implement fake local success or change the shared backend.

## Shared behavior: all screens

- Initial loading uses stable skeleton + accessible announcement, never zero money as loaded data. Refresh retains existing content.
- Offline uses one in-layout status with last successful refresh when known; no overlapping root banners or repeated status cards.
- Distinguish no records, no filter matches and unavailable data. Give one valid recovery/action. Existing data and editor drafts survive errors.
- Pending rows retain readable contrast with explicit status. Ambiguous writes must not be blindly replayed; preserve fresh-server reconciliation and NEEDS_REVIEW. Online persistence stays immediate. Undo/grace remains offline/local-only.
- Full-screen editors: one sticky Save above IME/insets, field-level errors, focus first invalid field, prevent double submit, preserve draft on failure. Every changed field counts as dirty. Close only after durable success/confirmed local enqueue.
- Single selectors and filters use sheets; substantial forms use full screens. Essential/destructive actions are never gesture-only. Confirmation names item and consequence.
- 150% font: minimum heights only, wrap Greek labels, stack trailing amounts where needed, no clipping. Touch targets >=48dp. TalkBack reads identity/amount/currency/status once with correct order. Charts have text equivalents.
- Dark changes tokens, not hierarchy. Positive/negative also have signs/labels; transfers neutral. No real finance secrets in previews, screenshots, logs or diagnostics.
- Motion is short, interruptible, reduced-motion aware and never delays persistence.

## Screen specifications

### H1 Home and attention

Current: oversized available hero, income/expense/net triplet, account sparklines and upcoming/attention repeat information. Goal: know usable money and immediate action.

Order: compact header + Settings/amount visibility; available amount with precise scope; New transaction; up to two actionable attention rows; three primary account rows + All accounts. If recent movements are retained, show at most three flat rows linked to shared detail, without another summary card. Remove triplet, account sparklines and duplicate upcoming section; analytics belongs in Κινήσεις.

Attention detail groups actual urgency and shows source/date/amount once. Open a supported recording/payment flow. Mark reviewed cannot imply paid, mutate canonical completion or claim persistent dismissal if UI-local. Omit empty attention. No fabricated account-creation action. Amount hiding must apply consistently beyond Home.

### H2 Activity, filters, transaction detail/editor

Current: incomplete filters, type resets on projection, detail immediately edits, category drill-down loses interval. Goal: find, understand and correct a movement.

Order: title + New; search; Filter with active count; removable scope summary; date-grouped flat ledger. Row: category icon, title/note, account or source→destination, amount/sign, pending marker. Transfers neutral; distinguish credit purchase/payment.

Filter sheet: exact account/type/category identity/date range, Apply/Reset. Preserve on refresh/rotation/tab switch. No-match state offers Clear filters; true-empty offers New. Analytics drill-down carries exact category+interval and returns to originating analysis scope.

Read detail order: amount/type/status; date; source/destination or account; category/subcategory; note; linked account/card; Edit; overflow Delete. All account/card ledger rows open it. Editor initially supports only existing date/note/category/subcategory mutation; amount/account/type remain read-only unless canonical support is verified. Pending records cannot partially edit date. Deletion confirmation is not described as bank reversal.

### H3 Quick Entry and split

Current: separate fast/advanced forms, selector overload, incomplete dirty tracking and stuck failure states. Goal: correct entry quickly.

Order: header/type; amount/currency; required account/source/target/card; applicable category/subcategory; date; collapsed optional note; sticky Save. Amount uses main-surface typography, not nested cards. Searchable picker sheets show identity/provider/last4 once. Visible Expense/Income/Transfer; More lists all kinds. Preserve compatible fields on type switch; explicitly reset incompatible ones.

Retain exact existing canonical mapping for EXPENSE, INCOME, TRANSFER, WITHDRAWAL, SAVING (saving_cash_offset), REFUND, LENDING, REPAYMENT, CARD_PURCHASE, CARD_PAYMENT, RECONCILIATION, SPLIT. Do not merge distinct accounting operations. Show only required fields. Transfers/withdrawal name both sides; saving explains actual offset; lending/repayment retain existing relationship fields; credit payment identifies payer+card; reconciliation explains balance versus delta using current contract.

Split dedicated editor: total/source/date; editable amount/category line rows; Add line; live remaining allocation; Save only when exact existing currency validation passes. Test locale decimals, rounding, invalid amounts, all kinds, draft loss, duplicate submit, online/offline/reconnect. Remove intentional online Undo delay.

### H4 Wallet/accounts/net position

Current: net-position hero, decomposition, savings and card stack compete. Goal: identify money, accounts and debt.

Accounts tab: header; compact available total/scope; flat daily/savings/other account groups as supported; Net position link. Row: provider mark, account name, one useful secondary identity, balance. Cash distinct. No repeated provider badges or invented savings goals.

Account detail: identity; balance; contextual New transaction; account-scoped date-grouped ledger. Optional verified history behind expansion, not a mini-chart on every overview row. Account-relative signs; transfers open shared detail. No unsupported IBAN/account-admin controls.

Net-position detail: total/date/scope; assets (accounts/receivables); liabilities (loans/credit); expandable calculation. Never derive debt solely from visible active cards. Preserve inactive/unallocated debt per canonical semantics. Missing source data means unavailable/partial, not a false total.

### H5 Cards overview/detail/switching

Replace ReferenceCreditCardStack swipe/restack/shredding with a vertical card list: stable identity, direct selection, readable credit amounts and accessible scaling.

Rows: provider/nickname/last4, useful type/network, trailing credit debt; debit has no invented balance. Add in header. Overview contains no reveal/copy/PAN/CVV/delete. Selection opens detail; labelled picker switches by stable ID, never cyclic swipe-only.

Detail: compact identity/art; credit amount owed + limit/available credit with precise semantics, or verified linked account; primary Pay card where applicable; secondary Add purchase; tappable card-scoped movements + See all; secure details entry; overflow supported metadata edit and Remove from MyFinHub. No freeze/issuer cancellation/wallet provisioning. Recover clearly if selected card becomes inactive.

Provider assets in contained 40dp mark; neutral initials fallback. App accent controls actions; provider colors identify, not decorate every surface. Long Greek names wrap at 150%.

### H6 Secure details/create/edit/removal

Secure separate protected destination. Hidden default, explicit Reveal, labelled PAN/expiry/device-local CVV, per-field Copy, Hide. Current reveal uses session auth; do not claim an already-implemented extra biometric challenge. Hide on background/lock/navigation and proposed 30-second reveal window, verifying lifecycle and accessibility. Protect reveal/edit screenshots; mark clipboard sensitive and use supported expiry/cleanup without clearing unrelated clipboard content.

Server PAN/expiry and encrypted device-local CVV have separate save/delete outcomes. Partial failure shows which part needs recovery, without replaying successful finance writes or exposing values. Auth rejection leads to session recovery, not endless generic Retry.

Create full-screen: nickname/provider (existing five + custom), kind/network/physical-or-virtual/last4, conditional credit limit, one Save. Sheets replace chip walls. Safe metadata separated from secrets. Failure releases submitted lock and preserves draft; success navigates once by created ID. Metadata editing shares form only after supported mutation verification; otherwise record missing capability.

Remove confirmation states removal from active MyFinHub list, retained history/debt and secret cleanup, never bank cancellation. Deactivate canonically, then handle server/local purge results separately. Inactive debt remains in totals. Do not repeat deactivation to retry only purge. No shred animation. Historical movements remain accessible.

### H7 Plan/forecast/budget/savings/debts

Plan order: overdue/soon rows; 30-day forecast link; overall monthly budget link; remaining items grouped by date. Show each source once. Details expose supported contextual recording, not fake completion/recurring editors.

Forecast detail: opening available, planned income, obligations, neutral transfer effects, closing available; exact interval; expandable rows. Compute all eligible items BEFORE display limits. Investigate current take(20) and title/amount/date dedup that can collapse different sources/accounts. Correct Android projection only against existing canonical identity/semantics. No arbitrary horizons or invented recurring occurrences. Label partial/unavailable forecasts.

Budget: monthly spend, remaining/over, progress, short context; Edit amount and existing 1–100 threshold. Threshold warning is not input-validation error. No unsupported push-alert promises/category-budget creation.

Wallet debts groups credit/loans owed/receivables distinctly. Aggregate-only production values remain labelled useful totals, not empty lists implying no debt. Real item details, where available: remaining amount, party/name, dates and related movements; repayment uses existing Quick Entry semantics. Savings stays in account group, no invented goal while canonical goal is null.

### H8 Analysis

Within Κινήσεις: period selector; primary spending+equivalent-interval comparison; categories; expandable income/net and monthly trend. Remove duplicate largest-category card and repeated per-row View buttons. Whole category row tappable with amount/share alignment. Exact comparison dates; partial-month labels; zero/unavailable baseline handled honestly. Top-N categories include Other so shares cover complete denominator. Text alternative for charts. Drill-down preserves exact category identity/date range and origin Back state.

### H9 Settings/diagnostics/notices/update/auth/root

Settings: appearance System/Light/Dark; global amount visibility; existing security; notices; version/update; diagnostics behind About/Diagnostics; separate Sign out. No developer status cards dominating the page.

Diagnostics summary first, expand safe details, sanitized Copy. Notice history maps safe code/time to readable Greek; do not call it financial change history. Synthetic ChangeHistory remains out of production.

Updater has one valid next action per state: Check/Download/progress/Verify/installation permission/Install/state-specific recovery. Auth required opens auth recovery; network retries; verification failure cannot install. Preserve trusted source/checksum/version/signing. Redesign does not publish releases.

Auth: focused login, six-digit TOTP, PIN4–12+confirm, native biometric+PIN fallback, lockout with real retry timing. Short Greek copy, no confusing password/TOTP/local PIN. Preserve AAL2/session/lifecycle. IME/150% never hides errors/primary action.

Root: stable initial load; recoverable no-cache Retry/Sign out; only genuinely supported cached/offline state; one in-layout sync region, no duplicated transient notices or synthetic finance fallback.

## Design system

System sans with Greek coverage. Proposed targets must be verified on actual renders.

| Role | Size/line height sp | Weight |
|---|---|---|
| Primary money | 34/40 | 600 |
| Detail money | 28/34 | 600 |
| Page | 24/30 | 600 |
| Section | 18/24 | 600 |
| Row | 16/22 | 500 |
| Body | 16/24 | 400 |
| Metadata | 14/20 | 400 |
| Button | 15/20 | 600 |
| Navigation | 12/16 | 500 |

Spacing dp: 4/8/12/16/20/24/32. Margin20, section24, group12, label/value4, icon/text12, row vertical12. Minimum row64/button52/field56/touch48; expand for text. Radii content16/small12/sheet24/card-art20. Flat lists/dividers, content elevation0, platform sheets/dialog elevation. Icons24. One filled primary; secondary tonal/outline/text by priority. Chips for selections/filters only.

| Role | Light | Dark |
|---|---|---|
| Background | #F6F7FA | #101216 |
| Surface | #FFFFFF | #191D24 |
| Secondary surface | #EEF1F5 | #242A34 |
| Text | #151922 | #F2F4F8 |
| Secondary text | #596273 | #B6BECC |
| Accent | #3659E3 | #A6B8FF |
| On accent | #FFFFFF | #14245E |
| Positive | #087443 | #73D6A1 |
| Negative | #B42335 | #FFADB8 |
| Warning | #895400 | #F4C56A |
| Control border | #7B8494 | #8792A5 |
| Divider | #E0E4EB | #343B48 |

Verify actual composited text contrast >=4.5:1 normal and >=3:1 large/essential controls. Not a claim these proposals are already certified. Signs/labels supplement color. Debt is not inherently an error. Motion120/180/240ms, reduced-motion aware; no count-up money or decorative shredding.

Shared inventory (reuse equivalents before new abstractions): ScreenScaffold, SectionHeader, AmountText, AccountRow, CardRow, TransactionRow, ScopeSummary, FilterSheet, SearchablePickerSheet, ReadDetailField, EditorField, PrimaryActionBar, InlineIssue, SyncStatus, PendingBadge, EmptyState, LoadingSkeleton, DestructiveConfirmation, CreditSummary, BudgetProgress, CategoryBarRow, accessible ChartSummary, SecureField, UpdateStatePanel. Replace stack/duplicate Quick Entry structures and financial hero/triplet only when routes/tests/reference checks permit. Preserve finance/auth/repository machinery.

## Ten implementation slices

Each has four stable subtasks in canonical tracking. Dependencies are execution order, not permission for repeated whole-repo audits.

| ID | Scope / dependencies | Four subtask outcomes | Acceptance evidence |
|---|---|---|---|
| S1 | Financial truth/capability; first | Projection completeness; exact filter scope; production capability map; regression verification | >20 plan records, distinct same-title/account items, inactive debt, refresh/date/category tests; no backend changes |
| S2 | Foundation/navigation; S1 | Tokens/shared rows; four-root routes; editor/back/state; accessibility verification | Real production light/dark/150% renders, touch/contrast, origin Back/state restoration |
| S3 | Activity; S1–S2 | Ledger/search/filter; read detail; supported edit/delete; verification | No-matches/pending/transfers/drafts/locale/detail navigation |
| S4 | Quick Entry; S1–S3 | Unified forms/pickers; all12 kinds/split; dirty/persistence; verification | Every mutation, IME/150%, queue vs online, no duplicate/ambiguous replay |
| S5 | Home/accounts; S2–S4 | Compact Home/attention; wallet/groups; account ledger/net-position entry; verification | Empty/error/offline/privacy/long identities/account-relative signs |
| S6 | Cards; S3–S5 | List/detail/switch; create/supported metadata edit; secure/removal; verification | Credit/debit/no/many cards, purge partial failure, retained debt/history, reveal/background/copy |
| S7 | Plan/debts; S1,S2,S4,S5 | Urgency/forecast; budget; savings/debts/claims; verification | Complete forecast/dedup, aggregate-only debt, thresholds, light/dark/150% |
| S8 | Analysis; S1–S3 | Interval comparison; categories/remainder; scope/trend drill-down; verification | Partial month/zero baseline/shares/origin Back/accessible charts |
| S9 | Settings/auth; S2,S6 | Settings/privacy/notices; diagnostics/update; auth/root; verification | PIN/TOTP/biometric/lockout/IME/update failures/no overlaps or leaked values |
| S10 | Integration; S1–S9 | Remove proven-obsolete paths; hosted gates; inspect/replace renders; physical S24 acceptance | References, CI/lint/build/instrumentation, light/dark/150%, upgrade/session/CVV/offline continuity and explicit owner acceptance |

S1 entrypoints: locate CanonicalPlanProjection.kt, CanonicalProductProjection.kt, CanonicalInsightsProjection.kt, FinanceProductViewModel.kt, MyFinHubApp.kt and their projection tests; direct dependencies canonical models/mutations and ActivityUiState. Audit findings are hypotheses to verify with tests, not permission to reinterpret formulas.

## Checkpoint and completion rules

### S1.1 implementation decision (2026-09-11)

The inspected canonical scheduled projection exposes no link to a recurring source. Matching title, cents, date or account cannot prove two independently keyed obligations are identical. Retain both until an existing explicit canonical relationship can be verified; do not invent a linkage or suppress amounts heuristically. Plan totals use the complete eligible list before horizon filtering, with no presentation cap in the projection.

Money total debt comes from the existing aggregate credit-account ledger balance (including snapshots), independent of active card visibility. Per-card amounts use the existing creditDebtForCardAt rule used by payment validation, including its legacy ownership rule. Never attribute the entire aggregate to the last active card. Snapshot/unallocated debt remains in the total even when no card can truthfully own it. No canonical write or backend formula is changed.

Current state records stable IDs, pending/in_progress/blocked/completed, evidence, exact branch/PR, blockers and next action. Counts derive from subtask state. Completed requires evidence; all four complete makes a slice complete. Preparation has separate four steps, never counted as Android UI implementation. Historical project4/6 stays separate from redesign0/10 and subtasks0/40.

At each coherent checkpoint run narrow checks, commit/push with tracking, update the same PR and persist exact next action before context/quota exhaustion. UI completion requires personally inspected actual Compose renders, not mockups or merely green goldens. Record unmerged/untested/physical-pending explicitly.

If an existing shared capability cannot support a specified action, record screen, missing contract and truthful current-functionality fallback; do not silently remove it or mark completed. No backend workaround. Stable/main promotion and publishing remain separate deliberate release checkpoints.
