# MyFinHub Android — 2026 full-app redesign/hardening handoff

## Start here in a new chat

1. Read permanent issue #27 first.
2. Read `docs/SUPPORTED_DEVICE.md` immediately after it.
3. Read issue #37 and PR #38 to determine whether integration is already complete.
4. Read `STATUS.md`, `TODO.md` and this file.
5. Inspect the actual PR/branch/workflow state before changing anything.
6. Do not restart redesign discovery or reinterpret confirmed scope decisions.

## Sole supported device

The Android app supports **only the owner's physical Samsung Galaxy S24 Ultra**.

- `docs/SUPPORTED_DEVICE.md` is the device-acceptance source of truth.
- Tablet, foldable and desktop-like Android layouts are unsupported and are not merge/release gates.
- A hosted stock-Android compact-phone emulator is representative automated infrastructure, not an exact Samsung One UI / S24 Ultra simulation.
- Actual device-specific rendering, display/font settings and performance are authoritative only on the owner's physical S24 Ultra during Phase 6.

## Completed workstream

The full retained native Android product has completed the 2026 redesign and post-review reliability/cleanup pass.

Completed scope includes:
- shared light/dark visual system, authentic MyFinHub branding and centralized icon vocabulary;
- redesign of Home, Activity, Money, Plan, Insights, Quick Entry, auth and retained secondary/detail/system flows;
- issue #24 credit-card stack contract preserved;
- Forecast and Backup/Import/Data Transfer Android UI removed;
- obsolete Phase-1 Bootstrap and obsolete Android-only Backup/Import API/test scaffolding removed where no longer required;
- confirmed exclusion of category administration/icon picker, Command Palette, desktop shortcut/mass-admin surfaces, Windows install/update/recovery and full desktop Reports;
- canonical/desktop-owned finance fields preserved losslessly;
- safe `UserNotice` operational-error contract across auth/session, finance sync and secure card-secret flows;
- global Material 3 Snackbar with `Λεπτομέρειες`, safe diagnostic dialog and inline field validation;
- malformed response, unexpected exception, finance projection/mutation and local CVV vault edge cases contained recoverably without leaking secrets;
- S24-only screenshot suite pruned of unsupported form-factor cases;
- real Snackbar/details screenshots visually inspected and accepted only after fixing timing, bottom-navigation overlap and Home FAB overlap;
- non-replay notice instrumentation tests made deterministic by subscribing before the failure action.

## Final automated acceptance

The completed implementation passed:
- canonical screenshot regression with the visually approved error-feedback references;
- the representative S24 Ultra-target compact-phone instrumentation suite, all 31 tests;
- normal Android CI including benchmark/Baseline Profile tooling build, unit tests, instrumentation compilation, lint, debug assembly, optimized unsigned release/R8 analysis and release-manifest/unsigned-APK policy audit.

No unresolved Samsung Galaxy S24 Ultra implementation blocker remains.

## Confirmed Android product exclusions

Unless explicitly reversed later, Android does not expose:
- Forecast / cash-flow forecast UI;
- full desktop Reports; lightweight Android Insights remains;
- category/subcategory administration;
- category icon picker / icon administration;
- Backup / Import / Data Transfer UI;
- Command Palette;
- desktop keyboard-shortcut workflows;
- desktop-style mass administration/configuration;
- Windows install/update/recovery functionality.

These exclusions never permit corruption or removal of unknown/desktop-owned canonical finance fields.

## Screenshot/validation rule

For future UI changes follow issue #27:
- only real rendered application screenshots count as implementation evidence;
- personally inspect them;
- replace stale references instead of accumulating obsolete screenshots;
- evaluate the supported S24 Ultra phone experience, not unsupported tablet/foldable layouts;
- do not present concepts/mockups as implementation evidence.

## Exact continuation rule

- If PR #38 is still open, confirm its current exact-state required checks remain green, synchronize issue #37/PR #38, and merge it into `develop` with no unresolved S24 Ultra blocker.
- If PR #38 is merged, the redesign/hardening workstream is finished. Do not reopen it merely to repeat discovery or unsupported-form-factor work.
- Subsequent production/device/signing work belongs to Phase 6 / issue #14 on the owner's physical Galaxy S24 Ultra.

## Release boundary

Phase 6 remains separate. Do not create a release, production signing key or production-signed APK outside the explicit Phase 6 handoff. Real production Auth/API and device-specific performance validation stays on the owner's physical Galaxy S24 Ultra.
