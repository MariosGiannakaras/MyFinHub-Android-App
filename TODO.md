# MyFinHub Android TODO

## Phases 0–5 — completed

- [x] Research/design foundation and repository workflow.
- [x] Kotlin/Compose project with Material 3/Adaptive, Navigation 3 and ViewModel/StateFlow/UDF.
- [x] Representative/full retained mobile product UX and frontend parity.
- [x] Production native-client backend gate and canonical finance integration.
- [x] Native auth, AAL2, biometric/PIN local unlock and encrypted session persistence.
- [x] Owner+AAL2 PAN/expiry boundary and device-local Keystore CVV vault.
- [x] Security/performance/release hardening through Phase 5, including R8, Baseline Profile/startup profile and Macrobenchmark infrastructure/evidence.
- [x] Pre-redesign validation evidence.

## Post-Phase 5 — Full-app 2026 UI redesign

Tracker: issue #37. Active workstream targets `develop`.

### Supported-device contract

- [x] Define the owner's Samsung Galaxy S24 Ultra as the sole supported Android device in `docs/SUPPORTED_DEVICE.md` and permanent issue #27.
- [x] Remove tablet/foldable form factors from implementation/merge acceptance scope.
- [x] Remove the automatic tablet/foldable/150%-font release UI matrix.
- [x] Keep one representative compact-phone instrumentation path for automated S24 Ultra-target smoke coverage.
- [x] Make hosted-emulator Baseline Profile/Macrobenchmark runs optional/manual diagnostics; retain benchmark/profile tooling compilation in normal CI.

### Shared system and retained product

- [x] Shared 2026 light/dark theme, typography, shapes, spacing and compact density.
- [x] Central semantic finance colors and helpers.
- [x] Final centralized curated/static `MyFinHubIcons` vocabulary aligned with the main product's Lucide-style language.
- [x] Authentic MyFinHub light/dark branding and launcher/adaptive icon resources; no invented replacement artwork.
- [x] Reusable compact screen/header/card/list-row/filter/amount/action components.
- [x] Home / Αρχική redesign.
- [x] Activity / Κινήσεις redesign.
- [x] Money / Χρήματα redesign while preserving issue #24 credit-card stack contract.
- [x] Plan / Πλάνο redesign.
- [x] Insights / Αναλύσεις redesign.
- [x] Home attention detail, Settings and Change History redesign.
- [x] Activity detail/edit and Quick Entry redesign.
- [x] Savings, Loans, Lending and editor/detail flows redesign.
- [x] Plan item detail/editor and Budgets/rules redesign.
- [x] Auth/login/TOTP/PIN/unlock and shared loading/failure/conflict states migrated without weakening security UX.

### Confirmed Android scope pruning

- [x] Remove Forecast UI/navigation/screens while preserving canonical document compatibility.
- [x] Remove Backup/Import/Data Transfer UI/navigation/screens and obsolete Android-only network endpoints while preserving canonical document compatibility.
- [x] Confirm no category/subcategory administration screen is exposed; category selection remains only in finance-entry/edit flows where required.
- [x] Confirm no category icon picker/icon administration is exposed.
- [x] Confirm no Command Palette, desktop keyboard-shortcut workflow, desktop mass-administration/configuration workspace or Windows install/update/recovery UI is exposed.
- [x] Keep lightweight `Insights / Αναλύσεις`; do not expand it into full desktop Reports.
- [x] Preserve unknown/desktop-owned canonical finance fields losslessly despite Android UI exclusions.

### Post-review reliability and clean-code hardening

- [x] Introduce one safe `UserNotice` contract for operational failures across auth/session, finance sync and secure card-secret flows.
- [x] Surface operation/system failures through a global Material 3 Snackbar with `Λεπτομέρειες`; keep field validation inline.
- [x] Restrict user-visible diagnostics to safe operation/category/HTTP/retry/code metadata and never expose raw server payloads, credentials, tokens, PAN or CVV.
- [x] Contain malformed network/auth success responses and unexpected API exceptions inside typed recoverable failures.
- [x] Preserve coroutine cancellation instead of converting lifecycle cancellation into a false application error.
- [x] Keep the last valid finance state when local mutation/projection fails; retain explicit retry/discard handling for sync conflicts and save failures.
- [x] Report secure local CVV vault read/save/delete failures instead of silently ignoring them, without leaking card secrets.
- [x] Remove obsolete Phase-1 Bootstrap scaffolding and obsolete Backup/Import Android API surface; retain only synthetic state holders still used by test/demo infrastructure.
- [x] Remove unsupported expanded/tablet/foldable screenshot cases and references from S24 Ultra acceptance.
- [x] Add unit coverage proving diagnostic messages do not leak raw failure/secret content.
- [x] Add phone screenshot evidence for error Snackbar and safe details dialog.
- [x] Fix screenshot timing so the Snackbar is deterministically rendered.
- [x] Fix Snackbar placement so it clears both the S24 phone bottom navigation and the Home floating primary action.

### Screenshot and final S24 Ultra validation

- [x] Regenerate real screenshots after scope pruning and branding/icon correction.
- [x] Personally inspect the regenerated compact, auth, Money, Plan, Insights and retained secondary-flow screenshots.
- [x] Fix the expanded Home quick-entry overlap found before the device scope was narrowed.
- [x] Align stale instrumented-test selectors with the redesigned search/accessibility semantics.
- [x] Pass canonical screenshot regression before the post-review hardening pass.
- [ ] Pass final exact-state screenshot regression with the new error-feedback references.
- [ ] Pass final exact-state representative compact-phone instrumentation for the S24 Ultra target.
- [ ] Confirm final normal Android verification.
- [ ] Synchronize issue #37 / PR #38 completion status after the final supported-device gates pass.
- [ ] Merge the full-app redesign into `develop` only after zero unresolved Samsung Galaxy S24 Ultra blockers.

Tablet/foldable/desktop-like Android form factors are not supported targets and are not TODO/merge gates. Do not reopen work solely for those layouts unless the same defect affects the Galaxy S24 Ultra.

### Confirmed Android exclusions — 2026-09-01

The following are intentionally not part of the Android user-facing product unless explicitly reversed later:
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

Tracker: issue #14. This phase remains separate and is not part of the redesign workstream.

The owner's physical Samsung Galaxy S24 Ultra is the authoritative device for all device-specific Phase 6 validation.

- [ ] All functional/security/quality work merged to `develop` and accepted for Phase 6.
- [ ] Validate production-configured build against real Auth/API on the Galaxy S24 Ultra.
- [ ] Freeze release versionCode/versionName and promote a release candidate only after Phase 6 gates pass.
- [ ] Document final Android Studio/JDK/SDK prerequisites and clean-clone validation.
- [ ] Perform the first physical-device run on the Galaxy S24 Ultra.
- [ ] Validate auth → biometric/PIN relaunch → canonical data sync → logout → re-auth on the physical Galaxy S24 Ultra.
- [ ] Validate actual Samsung One UI rendering plus the owner's current display resolution/zoom/font settings.
- [ ] Validate device-specific startup/performance behavior on the physical Galaxy S24 Ultra.
- [ ] Create/preserve a long-lived signing key only when release signing explicitly begins; keep it outside the public repository.
- [ ] Generate a signed APK only when explicitly requested during the Phase 6 handoff.

Do not create a release, production signing key or production-signed APK during the active redesign.
