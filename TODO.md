# MyFinHub Android TODO

## Phases 0–5 — completed

- [x] Research/design foundation and repository workflow.
- [x] Kotlin/Compose native product and retained mobile UX.
- [x] Production native-client backend gate and canonical finance integration.
- [x] Native auth, AAL2, biometric/PIN local unlock and encrypted session persistence.
- [x] Owner+AAL2 PAN/expiry boundary and device-local Keystore CVV vault.
- [x] Security/performance/release hardening through Phase 5, including R8, Baseline Profile/startup-profile and Macrobenchmark infrastructure/evidence.

## Post-Phase 5 — Full-app 2026 redesign and reliability hardening — completed

Tracker: issue #37. Integration PR: #38. Target: `develop`.

### Supported-device contract

- [x] Samsung Galaxy S24 Ultra is the sole supported Android device in `docs/SUPPORTED_DEVICE.md` and permanent issue #27.
- [x] Tablet/foldable/desktop-like Android form factors are outside implementation and merge acceptance scope.
- [x] Automatic tablet/foldable/150%-font adaptive CI was removed.
- [x] One representative compact-phone instrumentation path remains for automated S24 Ultra-target smoke coverage.
- [x] Hosted-emulator Baseline Profile/Macrobenchmark runs are manual diagnostics; normal CI still compiles the tooling.

### Product redesign and scope

- [x] Shared 2026 light/dark theme, typography, shapes, compact density, semantic finance colors, authentic branding and centralized icon vocabulary.
- [x] Home / Activity / Money / Plan / Insights redesign.
- [x] Quick Entry, auth and all retained secondary/detail/system flows redesigned.
- [x] Issue #24 credit-card stack contract preserved.
- [x] Forecast UI/navigation removed while canonical compatibility is preserved.
- [x] Backup/Import/Data Transfer UI/navigation and obsolete Android-only API surface removed.
- [x] Confirmed no category administration/icon picker, Command Palette, desktop shortcut/mass-admin, Windows install/update/recovery or full desktop Reports UI is exposed.
- [x] Unknown/desktop-owned canonical finance fields remain losslessly preserved.

### Post-review reliability and clean-code hardening

- [x] Shared safe `UserNotice` operational-error contract across auth/session, finance sync and secure card-secret flows.
- [x] Global Material 3 Snackbar with `Λεπτομέρειες` for operation/system failures; field validation remains inline.
- [x] Safe diagnostic metadata only; no raw server payloads, exception messages, credentials, tokens, PAN or CVV in user-visible errors.
- [x] Malformed network/auth responses and unexpected API/repository exceptions contained as typed recoverable failures.
- [x] Coroutine cancellation preserved instead of being converted into false application failures.
- [x] Last valid finance state retained when local mutation/projection fails; explicit retry/discard behavior preserved for sync problems.
- [x] Secure local CVV vault read/save/delete failures surfaced safely rather than silently ignored.
- [x] Obsolete Phase-1 Bootstrap scaffolding and obsolete Backup/Import Android API/tests removed; still-used synthetic test/demo state retained and documented.
- [x] Unsupported expanded/tablet/foldable screenshot cases removed from S24 Ultra acceptance.
- [x] Unit coverage verifies diagnostic messages do not leak raw failure/secret content.
- [x] Real phone screenshot evidence added for error Snackbar and safe details dialog.
- [x] Snackbar rendering made deterministic and positioned to clear both bottom navigation and the Home floating primary action.
- [x] Non-replay error-notice instrumentation tests made deterministic by subscribing before failure actions.

### Final automated acceptance

- [x] Final canonical screenshot regression passes with the visually approved error-feedback references.
- [x] Final representative S24 Ultra-target compact-phone instrumentation passes all 31 tests.
- [x] Final normal Android verification passes benchmark/profile tooling build, unit/instrumentation compile, lint, debug assembly, optimized unsigned release/R8 and release-manifest/unsigned-APK audit.
- [x] Zero unresolved Samsung Galaxy S24 Ultra blocker remains in the redesign/hardening implementation.

No autonomous implementation work remains in this workstream. PR/tracker state is the source of truth for integration/closure.

## Confirmed Android exclusions

Unless explicitly reversed later, Android intentionally omits:
- Forecast / cash-flow forecast.
- Full desktop Reports module; lightweight Android Insights remains.
- Category/subcategory administration.
- Category icon picker / icon administration.
- Backup / Import / Data Transfer UI.
- Command Palette.
- Desktop keyboard-shortcut UI/workflows.
- Desktop-style mass administration/configuration.
- Windows install/update/recovery functionality.

Do not strip desktop-owned/canonical fields merely because Android does not expose their UI.

## Phase 6 — Final production/release handoff

Tracker: issue #14. This phase remains separate from the completed redesign/hardening workstream.

The owner's physical Samsung Galaxy S24 Ultra is authoritative for all device-specific Phase 6 validation.

- [ ] Validate a production-configured build against real Auth/API on the Galaxy S24 Ultra.
- [ ] Freeze release versionCode/versionName and promote a release candidate only after Phase 6 gates pass.
- [ ] Document final Android Studio/JDK/SDK prerequisites and clean-clone validation.
- [ ] Perform the first physical-device run on the Galaxy S24 Ultra.
- [ ] Validate auth → biometric/PIN relaunch → canonical data sync → logout → re-auth on the physical Galaxy S24 Ultra.
- [ ] Validate actual Samsung One UI rendering plus the owner's current display resolution/zoom/font settings.
- [ ] Validate device-specific startup/performance behavior on the physical Galaxy S24 Ultra.
- [ ] Create/preserve a long-lived signing key only when release signing explicitly begins; keep it outside the public repository.
- [ ] Generate a signed APK only when explicitly requested during the Phase 6 handoff.

Do not create a release, production signing key or production-signed APK before the explicit Phase 6 signing handoff.
