# MyFinHub Android — 2026 full-app UI redesign handoff

## Start here in a new chat

1. Read permanent issue #27 first.
2. Read `docs/SUPPORTED_DEVICE.md` immediately after it.
3. Read issue #37 and PR #38.
4. Read `STATUS.md`, `TODO.md` and this file.
5. Inspect the actual PR/branch/workflow state before changing anything.
6. Continue from the real state; do not restart discovery or reinterpret confirmed scope decisions.

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

These exclusions do not permit Android to corrupt or strip canonical finance data. Unknown and desktop-owned fields must round-trip losslessly.

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
- shared 2026 light/dark visual system, authentic branding and centralized icon vocabulary;
- redesign of all retained top-level, secondary, auth and system flows;
- removal of Forecast and Backup/Import/Data Transfer user-facing Android routes/screens;
- verification of all confirmed Android exclusions while preserving canonical finance fields losslessly;
- Samsung Galaxy S24 Ultra declared as the sole supported device and automatic tablet/foldable/150%-font CI removed;
- one representative compact-phone instrumentation path retained and hosted-emulator performance/profile runs made manual diagnostics;
- post-review cleanup of obsolete Phase-1 Bootstrap and obsolete Backup/Import Android API surface;
- shared safe `UserNotice` error contract across auth/session, finance sync and secure card-secret flows;
- typed containment of malformed network/auth responses and unexpected repository/API failures while preserving coroutine cancellation;
- recoverable finance mutation/projection behavior that retains the last valid state;
- secure CVV vault read/save/delete failure reporting without leaking card secrets;
- global Material 3 Snackbar for operation/system failures with `Λεπτομέρειες`, while field validation remains inline;
- safe details dialog exposing only operation/category/HTTP/retry/diagnostic-code metadata, never raw responses, credentials, tokens, PAN or CVV;
- dedicated phone screenshot evidence for Snackbar and details dialog;
- screenshot timing fixed to render Snackbar deterministically;
- personal visual inspection caught and fixed Snackbar collisions with both bottom navigation and the Home floating primary action;
- the final visually approved error-feedback renders are now committed as canonical screenshot references.

## Screenshot/validation rule

For UI changes follow issue #27:
- only real rendered application screenshots count as implementation evidence;
- personally inspect them;
- replace stale references instead of accumulating obsolete screenshots;
- evaluate the supported S24 Ultra phone experience, not unsupported tablet/foldable layouts;
- do not present concepts/mockups as implementation evidence.

## Exact remaining work

1. Confirm screenshot regression passes with the newly committed, visually approved error-feedback references.
2. Confirm the representative compact-phone instrumentation suite passes on the same final state.
3. Confirm normal Android CI passes on the same final state.
4. Synchronize issue #37 / PR #38 completion text.
5. Merge into `develop` only after zero unresolved Samsung Galaxy S24 Ultra blockers, then close issue #37.

Hosted emulator performance/profile runs are not redesign merge blockers. Actual device-specific performance belongs to Phase 6 on the physical Galaxy S24 Ultra.

## Active workstream

- Tracker: issue #37.
- PR: #38.
- Base: `develop`.

## Release boundary

Phase 6 remains separate. Do not create a release, production signing key or production-signed APK as part of this redesign. The first physical-device and production Auth/API validation stays in the later Phase 6 handoff on the owner's Galaxy S24 Ultra.

Update this file whenever the active implementation, validation state, scope decisions or exact next action changes so a memoryless chat can continue safely.
