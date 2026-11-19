# ADR-0002: Simplified three-destination Android companion

Status: Supersedes the former five-root decision, per owner direction 2026-11-10.

## Decision

The Android application is a lightweight daily-use companion to the canonical Web app, not a mobile copy of its reporting and administration workspaces. Its primary navigation has **three** destinations:

1. **Αρχική** — today's available cash/payroll balance, the **three Web-matching primary account cards** (cash, payroll, savings) with their account-history graphs, 30-day change, savings comparison/goal, account details and account-specific actions; secondary accounts as short rows; at most two actionable concerns and four recent transactions, plus the short path to Quick Entry.
2. **Κινήσεις** — transaction history, search, basic filters, details and edit where allowed, and Quick Entry.
3. **Κάρτες** — existing cards, a small current-debt glance for credit cards, and transaction/purchase/payment entry. Card creation, archival controls, historical statements and credit-rate administration are not primary mobile content.

The Home privacy toggle and an Android Settings shortcut remain available. Settings is a **secondary screen**, not a fourth top-level destination. It includes device appearance, amount visibility, privacy/security information, Android notification controls, available app updates, version information and sign-out. Detailed analytics, budgets, recurring configuration, finance taxonomy, providers, icon administration and financial backup management belong to Web.

## Compatibility and safety

- Keep canonical financial projections and their ledger, auth, offline, vault, update and account security invariants unchanged.
- Keep existing route identities and domain screens in source for state/restoration and independent regression coverage. Removing them from the visible navigation does not delete the user's data or server capabilities. Their old top-level enumeration values remain recognized, but are not in `primaryMobileDestinations`.
- A contextual attention action may open a relevant source screen as a secondary route, not an additional tab.
- Existing signed-in installations with a saved selection of the former Plan or Insights destination return safely to Home.
- Legacy Plan and Insights screens remain covered through an isolated **internal test-only initial destination**. Production entry points do not pass that override: users see only the three primary tabs, while old retained domain logic can be tested without re-exposing desktop navigation.
- Only the authoritative Web app owns long-form financial administration. Android's own app/device Settings remain available, but must never be required to enter a routine expense, income or transfer.
- Preserve the Web's per-account actions in the three primary Home cards alongside the global Quick Entry. Keep adequate touch targets, responsive Compose layout, privacy masking (amounts hidden but chart geometry remains), system back and accessible labels.
- **Specific visual parity exception:** simplify the mobile navigation and reporting, **not** the Web-designed primary Home accounts. Their graphs, period changes, cash/payroll/savings roles, target and per-account navigation must not be dropped in later mobile simplification passes.

## Reason for replacing the prior ADR

The earlier five-destination approach optimized for Web feature parity. The owner explicitly clarified that Android is intended for quick balance checking and adding transactions, **without complex Web financial settings or detailed financial analysis**. Native Android app/device settings, support information and sign-out **must remain accessible** through a secondary Settings screen. Three primary destinations serve daily tasks directly without rebuilding the desktop administration interface on a phone.

## Validation

The new three-tab companion interaction is covered by unit and Android instrumentation tests. No successful build, CI, device rendering, or physical sign-off is implied by authoring those tests. All existing release and installation-safety gates remain in force.

## November 19, 2026 — source-only interaction refresh

The November compact-UI follow-up preserves this three-destination companion model and its existing finance/security routes. The Settings presentation uses flat Material 3 rows and direct switches instead of nested cards and full-width secondary actions. Update/install rejection, local amount privacy, Android notification settings, notice history, diagnostics, sign-out and package identity remain unchanged at the interaction contract level.

This is an **unvalidated source checkpoint**: no builds, tests, lint, screenshots, device acceptance or static-analysis commands were executed, per explicit owner instruction. Source work is recorded separately from completed historical UI acceptance and pending physical release acceptance in `tracking/android-project-state.json`.

### Active Activity and Cards hierarchy

- Activity new-entry action lives in the page header; search and filtering occupy the top of the history list. At large font scales, the header action uses an accessible icon rather than compressing the title.
- The three-tab companion Cards route keeps Quick Entry in the page header. The selected credit card retains purchase and payoff entry but avoids a second oversized primary card/action surface; debit cards do not show an irrelevant debt summary.
- Card detail keeps distinct purchase and payment actions; unavailable repayment is explained without a disabled full-width button. Card switching remains discoverable and separate from sensitive details.
- All changes are authored source only; no screen was rendered, built or executed in this pass.

### Existing screen and interaction inventory

Production navigation remains **Αρχική / Κινήσεις / Κάρτες**, as recorded above. The source-only review used the route definitions, NavDisplay wiring, screen implementations and existing mobile design contract. This is a source inspection, not rendered-screen or accessibility verification.

| Surface | Source-level treatment |
| --- | --- |
| Home, three primary account cards, secondary accounts, recent transactions | Preserve primary-account Web matching graphs, account semantics, privacy and contextual actions; reduce section gaps, wrap recent labels and separate chart from amount when font size grows |
| Home attention and contextual Plan Scheduled entry | Preserve real attention navigation, reviewed-only semantics and canonical obligation state; replace oversized temporary-dismiss action and repeated disclaimer with concise copy |
| Activity history/search/filter/month summary/category drilldown | Keep search/scope/month/filter state and canonical projection; move New entry into an adaptive header; never conflate filtered list with whole-month summary |
| Activity read detail, edit and deletion | Preserve read detail, existing partial editable fields, dirty state, confirmation and mutation barriers; no new finance operations |
| Quick Entry all twelve existing kinds, Split | Retain single editor, prefilled context, sticky Save, blocked-sync warning and canonical actions; wrap type chips and compact secondary entry actions; ensure note can collapse while its draft remains unchanged |
| Accounts, account detail, net position | Keep account grouping, actual balances, histories and source-aware net figures; put account balance ahead of optional IBAN actions and remove repeated scoped-ledger explanation |
| Cards companion, card detail, card switcher | Keep explicit stable-card selection, credit amount and contextual purchase/payment; move global Quick Entry to header, reduce duplicate identity/secondary controls and stack credit metrics at large font |
| Secure card details, card create, metadata editing and removal | Preserve protected vault/auth/clipboard/window lifecycle, delete confirmation and distinct persistence contracts; card creation explanatory copy is compact and avoids implying secrets appear on overview |
| Settings, update checker, Android notifications, notices and diagnostics | Replace large cards/secondary buttons with accessible settings rows; preserve local preference persistence, updater state/fail-closed rejection and safe support codes; collapse optional diagnostics |
| Auth login, MFA, local PIN/biometric, loading/empty/error/pending | Leave security-sensitive logic and existing state-specific controls intact; these are not navigation shortcuts or candidates for redundant-action removal |
| Savings, loan/lending detail, Plan/forecast/recurring/budgets, Insights | Retain source/route identities and contextual navigation compatibility. Reporting/administration remain Web-owned for the production three-tab companion; do not reintroduce these as primary destinations |

**Scope and evidence:** Source presentation only. No raw owner recordings, payment-card records, canonical financial calculations, offline queue, auth, updater verification logic, backend contracts, signing, publisher identity, package identity or release workflow was changed. No build, test, static analysis, lint, screenshot comparison, emulator run or physical S24 acceptance was performed. No PR, release or APK was created. Source commits remain on the feature branch until separately integrated through the repository workflow.
