# MyFinHub Android — 2026 full-app UI redesign handoff

## Start here in a new chat
1. Read permanent issue #27 first.
2. Read issue #37 (`Android 2026 UI system — full-app compact redesign`).
3. Read draft PR #38 (`ui: full-app 2026 redesign foundation`).
4. Read `STATUS.md`, `TODO.md` and this file.
5. Inspect the actual PR/branch/workflow state before changing anything.
6. Continue the active redesign; do not restart discovery or reinterpret the approved visual direction.

## Scope
The redesign covers the **entire existing native Android application**, not only Activity/Κινήσεις.

Top-level destinations:
- Home / Αρχική
- Activity / Κινήσεις
- Money / Χρήματα
- Plan / Πλάνο
- Insights / Αναλύσεις

Secondary/detail flows also belong to the redesign: Home attention/detail, Settings, Data Transfer, Change History, Activity detail/edit, Quick Entry, card detail/surrounding Money surfaces, Savings, Loans, Lending, Plan item detail/editor, Budgets/rules, Forecast, auth/login/TOTP/PIN/unlock surfaces where appropriate, and shared loading/failure/conflict states.

Issue #24 remains the source of truth for the special native credit-card stack component; do not silently redesign that component away.

## Approved visual direction
The user selected a combination of the earlier MyFinHub 2026 concept and the clean/modern Concept A transaction treatment.

- Preserve authentic MyFinHub branding/logo/product identity.
- Premium light-first visual language with restrained purple/indigo brand accent.
- Compact and modern, but **not packed**.
- Reduce wasted vertical space while preserving breathing room, 44dp-class touch targets where controls require them, readability and accessibility.
- Use a coherent curated/static icon system. Android does not need a desktop-style icon picker; icon mapping should be centralized and stable.
- Finance semantic colors:
  - income: green
  - expense: red/coral
  - savings: purple/indigo
  - transfer: blue
  - attention: amber
  - neutral/info: slate/gray
- Filters should use icon + label with clear selected/unselected treatment.
- Transaction/finance rows should use semantic leading icons and signed amount colors.
- Avoid default-Material-looking oversized cards/chips/headings when a more compact MyFinHub component exists.

## Architecture rule
Treat the Compose design system like a React design-system layer: future style changes must be centralized rather than copied into every screen.

Shared foundation owns:
- theme/color/typography/shape tokens;
- semantic finance colors;
- spacing/density tokens;
- `MyFinHubIcons` static registry;
- shared headers, cards, list rows, filter chips, amount text, icon badges and actions where appropriate;
- top-level navigation icon language.

Screens should consume shared tokens/components instead of hardcoding colors/icons/spacings independently.

## Product-scope rule
Do not automatically port every current/future desktop/web feature to Android. The main MyFinHub application is still changing and the user will decide mobile scope later. Desktop/admin-heavy features may intentionally remain desktop-only. Example: category icon selection can remain desktop-only while Android uses curated static icons.

This redesign changes the presentation of the **existing Android feature set**. It does not decide future feature parity.

## Validation rule
For any UI change follow issue #27:
- only real rendered app screenshots are implementation evidence;
- personally inspect the screenshots;
- replace stale references rather than accumulating obsolete screenshots;
- keep compact, foldable/tablet/adaptive and 150% font/accessibility coverage meaningful;
- do not claim concepts/mockups are implemented screenshots.

## Release boundary
Phase 6 remains separate. Do not create a production signing key, signed production APK or release as part of this UI redesign.

## Active workstream
- Tracker: issue #37.
- Draft PR: #38.
- Branch: `feature/full-app-2026-ui-redesign`.
- Base: `develop`.

## Implemented so far in PR #38
- 2026 light/dark theme with explicit MyFinHub palette, typography, shapes and compact spacing tokens.
- Central finance semantic palette for income/expense/savings/transfer/attention/neutral.
- Central `MyFinHubIcons` registry with custom static top-level and finance icons plus shared control icons.
- Top-level navigation now consumes the shared icon registry.
- Shared Compose components for screen headers, section cards, search, semantic filter chips, icon badges, finance rows, amount styling and primary actions.
- Activity / Κινήσεις and Activity detail are the first migrated consumers of the shared system.

**This is not the completion boundary.** PR #38 remains open until the rest of the existing application is migrated and validated.

## Remaining migration order
The exact internal order may change to keep builds green, but the scope must remain full-app:
1. Home + Home attention/detail + Utilities (Settings/Data Transfer/Change History).
2. Money surrounding surfaces + Savings/Loans/Lending/detail flows, while preserving issue #24 card-stack contract.
3. Plan + item editor + Budgets/rules + Forecast.
4. Insights.
5. Quick Entry.
6. Auth/login/TOTP/PIN/unlock and shared loading/failure/conflict states.
7. Final cross-screen consistency pass, real screenshot replacement and compact/adaptive/150%-font validation.

## Exact next action
Check the current PR #38 CI result first. Fix any shared-foundation compilation or UI regression before migrating the next screen group. Once the foundation is green, continue with Home and Utilities, then keep moving through the remaining groups without stopping after one page.

Update this file whenever the active PR, completed screen migrations, validation state or exact next action changes so a memoryless new chat can continue safely.