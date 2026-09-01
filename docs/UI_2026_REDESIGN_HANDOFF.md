# MyFinHub Android — 2026 full-app UI redesign handoff

## Start here in a new chat

1. Read permanent issue #27 first.
2. Read issue #37 and draft PR #38.
3. Read `STATUS.md`, `TODO.md` and this file.
4. Inspect the actual PR/branch/workflow state before changing anything.
5. Continue the active redesign from the real state; do not restart discovery or reinterpret the confirmed mobile-scope decisions.

## Scope

The redesign covers the entire retained user-facing native Android application.

Top-level destinations:
- Home / Αρχική
- Activity / Κινήσεις
- Money / Χρήματα
- Plan / Πλάνο
- Insights / Αναλύσεις

Retained secondary/detail flows include Home attention/detail, Settings, Change History, Activity detail/edit, Quick Entry, card detail and surrounding Money surfaces, Savings, Loans, Lending, Plan item detail/editor, Budgets/rules, auth/login/TOTP/PIN/unlock and shared loading/failure/conflict states.

Issue #24 remains the source of truth for the native credit-card stack component.

## Confirmed Android product-scope exclusions — user decision 2026-09-01

These are not Android user-facing features unless explicitly reversed later:
- Forecast / cash-flow forecast UI and navigation.
- Full desktop-style Reports module. Lightweight Android `Insights / Αναλύσεις` remains.
- Category/subcategory administration screens. Category selection remains inside finance-entry/edit flows where required by the canonical model.
- Category icon picker / icon administration.
- Backup / Import / Data Transfer UI and navigation.
- Command Palette.
- Desktop keyboard-shortcut UI/workflows.
- Desktop-style mass administration/configuration workspaces.
- Windows install/update/recovery functionality.

These exclusions do not permit Android to corrupt or strip canonical finance data. Unknown and desktop-owned fields must round-trip losslessly. Compatibility-only backend/domain code may remain when required for safe canonical behavior.

## Approved visual/system direction

- Preserve authentic MyFinHub branding/product identity.
- Premium light-first UI with restrained purple/indigo brand accent and full dark-theme support.
- Compact and modern, but not packed.
- Shared Compose design system owns theme, typography, shape, spacing, semantic finance colors, icon vocabulary, branding presentation and reusable components.
- Curated/static icon vocabulary is centralized in `MyFinHubIcons`; feature screens must not invent arbitrary one-off icon language.
- Finance semantics remain centralized: income green, expense red/coral, savings purple/indigo, transfer blue, attention amber, neutral/info slate/gray.
- Issue #24 credit-card stack contract remains intact.

## Current implementation state

Completed:
- shared 2026 light/dark theme, typography, shapes, compact spacing and finance semantics;
- final centralized curated/static `MyFinHubIcons` vocabulary aligned with the main product;
- authentic MyFinHub light/dark artwork and Android launcher/adaptive branding resources;
- shared compact headers, cards, list rows, filters, finance amount treatments, icon badges and actions;
- redesign of all retained top-level and secondary/auth/system flows;
- removal of Forecast and Backup/Import/Data Transfer user-facing Android routes/screens;
- verification that the other confirmed desktop/admin exclusions have no Android entry point;
- real screenshot refresh after branding/icon/scope correction and personal visual inspection;
- stale UI-test selector repair for icon-based back navigation, search placeholder semantics and duplicated Settings text;
- expanded Home overlap fix found during personal inspection: compact Home retains the floating `Νέα κίνηση` action, while expanded Home uses the in-card quick-entry primary action without a second floating action;
- emulator validation hardening: instrumentation APKs are prebuilt and Android package/activity service readiness is checked before compact/adaptive/150%-font suites.

## Screenshot/validation rule

For every UI change follow issue #27:
- only real rendered application screenshots count as implementation evidence;
- personally inspect them;
- replace stale references instead of accumulating obsolete screenshots;
- keep compact, foldable/tablet/adaptive and 150% font/accessibility coverage meaningful;
- do not present concepts/mockups as implementation evidence.

The latest UI change is the expanded Home action-overlap fix, so its previous screenshot reference is stale until regenerated and personally inspected.

## Exact remaining work

1. Let the screenshot renderer produce the current expanded Home candidate.
2. Replace the stale expanded Home reference with that real rendered image and personally verify the action overlap is gone.
3. Run/confirm screenshot regression on the committed new reference.
4. Confirm compact instrumentation passes with the corrected semantics selectors.
5. Confirm foldable, tablet and 150% font/accessibility suites pass with the hardened emulator workflow.
6. Confirm normal Android verification and performance/profile gates for the final implementation state.
7. Update issue #37 / PR #38 completion text so it no longer describes branding/icons/scope pruning as pending.
8. Merge the redesign into `develop` only after zero unresolved blockers.

## Active workstream

- Tracker: issue #37.
- Draft PR: #38.
- Base: `develop`.

## Release boundary

Phase 6 remains separate. Do not create a release, production signing key or production-signed APK as part of this redesign. The first physical-device and production Auth/API validation stays in the later Phase 6 handoff.

Update this file whenever the active implementation, validation state, scope decisions or exact next action changes so a memoryless chat can continue safely.
