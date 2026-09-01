# MyFinHub Android — 2026 full-app UI redesign handoff

## Start here in a new chat
1. Read permanent issue #27 first.
2. Read issue #37 (`Android 2026 UI system — full-app compact redesign`).
3. Read draft PR #38 (`ui: full-app 2026 redesign foundation`).
4. Read `STATUS.md`, `TODO.md` and this file.
5. Inspect the actual PR/branch/workflow state before changing anything.
6. Continue the active redesign; do not restart discovery or reinterpret the approved visual direction or the mobile-scope decisions below.

## Scope
The redesign covers the **entire user-facing native Android application that remains in the approved mobile scope**, not only Activity/Κινήσεις.

Top-level destinations currently retained:
- Home / Αρχική
- Activity / Κινήσεις
- Money / Χρήματα
- Plan / Πλάνο
- Insights / Αναλύσεις

Secondary/detail flows in scope include Home attention/detail, Settings, Change History, Activity detail/edit, Quick Entry, card detail/surrounding Money surfaces, Savings, Loans, Lending, Plan item detail/editor, Budgets/rules, auth/login/TOTP/PIN/unlock surfaces where appropriate, and shared loading/failure/conflict states.

Issue #24 remains the source of truth for the special native credit-card stack component; do not silently redesign that component away.

## Confirmed Android product-scope exclusions — user decision 2026-09-01
These are **not Android features** unless the user explicitly changes the decision later:

- Forecast / cash-flow forecast UI and navigation.
- Full desktop-style Reports module. The existing lightweight Android `Insights / Αναλύσεις` destination remains for now; do not expand it into desktop Reports unless explicitly requested.
- Category/subcategory administration screens.
  - Category selection inside transactions, Quick Entry, budgets or other finance records remains where required by the canonical finance model.
  - Android does not provide category taxonomy administration.
- Category icon picker / icon administration. Android uses a curated static mapping owned by the shared design system.
- Backup / import / data-transfer user interface and navigation.
- Command Palette.
- Desktop keyboard-shortcut UI/workflows.
- Desktop-style mass administration/configuration workspaces.
- Windows install/update/recovery functionality.

These exclusions are UI/product-scope decisions, **not permission to corrupt or strip canonical finance data**. Android must continue to preserve unknown/desktop-owned fields losslessly when reading/writing the canonical finance document. Existing backend/domain compatibility code may remain when it is required for safe canonical round-tripping even if Android exposes no corresponding screen.

Features not listed above remain undecided unless already part of the retained Android product. Do not remove additional functionality by inference.

## Branding and icon correction required before redesign completion
The current redesign must **not** be merged as final until branding/icons are corrected.

- Preserve the authentic MyFinHub project artwork from the main MyFinHub repository; do not invent a replacement logo.
- Android must have proper launcher/adaptive icon resources and use the authentic MyFinHub mark/lockup in appropriate shell/auth surfaces.
- Replace provisional/ad-hoc custom icon shapes with a coherent curated static icon language aligned with the main product's Lucide-style visual vocabulary.
- Keep all Android icon selection centralized behind `MyFinHubIcons` / shared semantic mappings; feature screens must not choose arbitrary one-off Material icons.
- Finance semantic icon mappings must cover at least income, expense, transfer, savings, account, card, loan, lending, plan, attention, settings and common controls.

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
- top-level navigation icon language;
- authentic MyFinHub branding resources/presentation contract.

Screens should consume shared tokens/components instead of hardcoding colors/icons/spacings independently.

## Product-scope rule
Do not automatically port every current/future desktop/web feature to Android. Android intentionally remains a focused mobile client over the same canonical finance state. Desktop/admin-heavy features may remain desktop-only.

Use the confirmed exclusion list above as the current source of truth. Do not treat differences from desktop/web as Android TODO items unless the user explicitly approves them.

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

## Current implementation state
- Shared 2026 light/dark theme, typography, shapes, compact spacing and semantic finance palette are present.
- A central `MyFinHubIcons` registry exists but is **provisional and must be corrected** to the approved coherent icon language before merge.
- Top-level navigation and multiple screens consume the shared foundation.
- Home, Activity, Money, Plan, Insights, Quick Entry, auth presentation and utility surfaces have redesign work in the branch.
- Rendered screenshot references were regenerated during the redesign, but they are **not final evidence** because branding/icon corrections and the confirmed feature removals still need to be reflected first.
- Forecast and Backup/Import are now confirmed out of Android scope and their user-facing entry points/screens must be removed from the active branch.

## Remaining work order
1. Remove Forecast user-facing card/route/screen references from Android navigation and screenshot expectations while preserving canonical data compatibility.
2. Remove Backup/Import/Data Transfer user-facing entry points/routes/screens and related screenshot expectations while preserving any compatibility-only canonical/API code that is still required.
3. Ensure no category-administration/icon-picker, command-palette, keyboard-shortcut, desktop mass-admin, Windows update/recovery or full Reports UI is exposed on Android.
4. Import/use authentic MyFinHub branding correctly, including launcher/adaptive icon resources and shared brand presentation.
5. Replace provisional icon drawings with the approved coherent centralized static icon vocabulary and update semantic mappings.
6. Re-render all affected real screenshots, personally inspect them, replace stale references and run exact-head compact/adaptive/150%-font/accessibility validation.
7. Update STATUS/TODO/issue #37/PR #38, then merge only with zero unresolved blockers.

## Exact next action
Continue on `feature/full-app-2026-ui-redesign`. First remove the newly excluded Forecast and Backup/Import user-facing surfaces and update affected navigation/tests. Then correct authentic branding and the icon system before another screenshot-baseline refresh. Do not merge PR #38 before those changes and final validation.

Update this file whenever the active PR, completed screen migrations, mobile-scope decisions, validation state or exact next action changes so a memoryless new chat can continue safely.