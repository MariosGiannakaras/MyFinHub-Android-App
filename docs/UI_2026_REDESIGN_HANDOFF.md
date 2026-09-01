# MyFinHub Android — 2026 full-app UI redesign handoff

## Start here in a new chat

1. Read permanent issue #27 first.
2. Read `docs/SUPPORTED_DEVICE.md` immediately after it.
3. Read issue #37 and PR #38.
4. Read `STATUS.md`, `TODO.md` and this file.
5. Inspect the actual PR/branch/workflow state before changing anything.
6. Continue the active redesign from the real state; do not restart discovery or reinterpret confirmed scope decisions.

## Sole supported device

The Android app supports **only the owner's physical Samsung Galaxy S24 Ultra**.

- `docs/SUPPORTED_DEVICE.md` is the source of truth for device acceptance scope.
- Tablet, foldable and desktop-like Android layouts are unsupported and are not merge/release gates.
- Do not spend work or CI time fixing problems that only occur on unsupported form factors unless the same defect affects the Galaxy S24 Ultra.
- A hosted stock-Android compact-phone emulator is only a representative automated host; it is not an exact Samsung One UI / S24 Ultra simulation.
- Actual device-specific rendering, display/font settings and performance are authoritative only on the owner's physical S24 Ultra during Phase 6.

## Scope

The redesign covers the entire retained user-facing native Android application on the supported S24 Ultra phone target.

Top-level destinations:
- Home / Αρχική
- Activity / Κινήσεις
- Money / Χρήματα
- Plan / Πλάνο
- Insights / Αναλύσεις

Retained secondary/detail flows include Home attention/detail, Settings, Change History, Activity detail/edit, Quick Entry, card detail and surrounding Money surfaces, Savings, Loans, Lending, Plan item detail/editor, Budgets/rules, auth/login/TOTP/PIN/unlock and shared loading/failure/conflict states.

Issue #24 remains the source of truth for the native credit-card stack component.

## Confirmed Android product-scope exclusions

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
- updated expanded Home screenshot reference rendered/replaced and personally inspected;
- canonical screenshot regression passed after the reference replacement;
- Samsung Galaxy S24 Ultra declared as the sole supported device;
- automatic tablet/foldable/150%-font adaptive matrix removed;
- one representative compact-phone instrumentation path retained for automated phone smoke coverage;
- hosted-emulator Baseline Profile/Macrobenchmark workflows changed to manual diagnostic runs; normal CI still verifies the tooling builds.

## Screenshot/validation rule

For UI changes follow issue #27:
- only real rendered application screenshots count as implementation evidence;
- personally inspect them;
- replace stale references instead of accumulating obsolete screenshots;
- evaluate the supported S24 Ultra phone experience, not unsupported tablet/foldable layouts;
- do not present concepts/mockups as implementation evidence.

## Exact remaining work

1. Confirm final exact-state screenshot regression after the S24 Ultra CI/scope update.
2. Confirm the single representative compact-phone instrumentation suite passes.
3. Confirm normal Android CI passes on the final exact state.
4. Update issue #37 / PR #38 completion text to reflect S24 Ultra-only acceptance and completed screenshots.
5. Merge the redesign into `develop` only after zero unresolved S24 Ultra blockers.

Hosted emulator performance/profile runs are not redesign merge blockers. They remain available manually; actual device-specific performance belongs to Phase 6 on the physical Galaxy S24 Ultra.

## Active workstream

- Tracker: issue #37.
- PR: #38.
- Base: `develop`.

## Release boundary

Phase 6 remains separate. Do not create a release, production signing key or production-signed APK as part of this redesign. The first physical-device and production Auth/API validation stays in the later Phase 6 handoff on the owner's Galaxy S24 Ultra.

Update this file whenever the active implementation, validation state, scope decisions or exact next action changes so a memoryless chat can continue safely.
