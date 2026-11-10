# ADR-0002: Simplified three-destination Android companion

Status: Supersedes the former five-root decision, per owner direction 2026-11-10.

## Decision

The Android application is a lightweight daily-use companion to the canonical Web app, not a mobile copy of its reporting and administration workspaces. Its primary navigation has **three** destinations:

1. **Αρχική** — today's available cash/payroll balance, account balances, at most two actionable concerns and four recent transactions, plus the short path to Quick Entry.
2. **Κινήσεις** — transaction history, search, basic filters, details and edit where allowed, and Quick Entry.
3. **Κάρτες** — existing cards, a small current-debt glance for credit cards, and transaction/purchase/payment entry. Card creation, archival controls, historical statements and credit-rate administration are not primary mobile content.

The Home privacy toggle remains available. Session sign-out is a small account menu action; there is **no Settings screen entry point in normal Android navigation**. Detailed analytics, budgets, recurring configuration, finance taxonomy, providers, icon administration and backup management belong to Web.

## Compatibility and safety

- Keep canonical financial projections and their ledger, auth, offline, vault, update and account security invariants unchanged.
- Keep existing route identities and domain screens in source for state/restoration and independent regression coverage. Removing them from the visible navigation does not delete the user's data or server capabilities. Their old top-level enumeration values remain recognized, but are not in `primaryMobileDestinations`.
- A contextual attention action may open a relevant source screen as a secondary route, not an additional tab.
- Existing signed-in installations with a saved selection of the former Plan or Insights destination return safely to Home.
- Only the authoritative Web app owns long-form administration. The Android user should never need a Settings section to enter a routine expense, income or transfer.
- Preserve one clear main Quick Entry action per active screen, adequate touch targets, responsive Compose layout, privacy masking, system back and accessible labels.

## Reason for replacing the prior ADR

The earlier five-destination approach optimized for Web feature parity. The owner explicitly clarified that Android is intended for quick balance checking and adding transactions, **without settings or detailed financial analysis**. Three destinations serve those tasks directly, reduce navigation cost and avoid building a second desktop finance management interface on a phone.

## Validation

The new three-tab companion interaction is covered by unit and Android instrumentation tests. No successful build, CI, device rendering, or physical sign-off is implied by authoring those tests. All existing release and installation-safety gates remain in force.
