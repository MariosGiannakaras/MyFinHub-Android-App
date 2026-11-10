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
