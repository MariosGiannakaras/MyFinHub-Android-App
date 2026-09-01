# MyFinHub Android TODO

## Phases 0–5 — completed

- [x] Research/design foundation and repository workflow.
- [x] Kotlin/Compose project with Material 3/Adaptive, Navigation 3 and ViewModel/StateFlow/UDF.
- [x] Representative/full retained mobile product UX and frontend parity.
- [x] Production native-client backend gate and canonical finance integration.
- [x] Native auth, AAL2, biometric/PIN local unlock and encrypted session persistence.
- [x] Owner+AAL2 PAN/expiry boundary and device-local Keystore CVV vault.
- [x] Security/performance/release hardening through Phase 5, including R8, Baseline Profile/startup profile and Macrobenchmark infrastructure/evidence.
- [x] Pre-redesign compact/foldable/tablet/150%-font/accessibility validation.

## Post-Phase 5 — Full-app 2026 UI redesign

Tracker: issue #37. Active workstream targets `develop`.

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
- [x] Remove Backup/Import/Data Transfer UI/navigation/screens while preserving compatibility-only backend/domain code where required.
- [x] Confirm no category/subcategory administration screen is exposed; category selection remains only in finance-entry/edit flows where required.
- [x] Confirm no category icon picker/icon administration is exposed.
- [x] Confirm no Command Palette, desktop keyboard-shortcut workflow, desktop mass-administration/configuration workspace or Windows install/update/recovery UI is exposed.
- [x] Keep lightweight `Insights / Αναλύσεις`; do not expand it into full desktop Reports.
- [x] Preserve unknown/desktop-owned canonical finance fields losslessly despite Android UI exclusions.

### Screenshot and final validation

- [x] Regenerate real screenshots after scope pruning and branding/icon correction.
- [x] Personally inspect the regenerated compact, large-font, auth, Money, Plan, Insights and expanded screenshots.
- [x] Fix the expanded Home quick-entry overlap found during personal screenshot inspection.
- [x] Align stale instrumented-test selectors with the redesigned search/accessibility semantics.
- [x] Harden emulator validation against package/activity-service startup failures and avoid running the expensive suites before instrumentation APKs are built.
- [ ] Regenerate/replace the expanded Home screenshot reference after the overlap fix and personally validate the new rendered result.
- [ ] Pass final current-state screenshot regression.
- [ ] Pass final current-state compact instrumentation.
- [ ] Pass final current-state foldable and tablet suites.
- [ ] Pass final current-state 150% font/accessibility suite.
- [ ] Confirm final normal verification and performance/profile gates.
- [x] Synchronize STATUS/TODO/handoff with the actual implementation state before final screenshot/reference validation.
- [ ] Synchronize issue #37 / PR #38 completion status after all gates pass.
- [ ] Merge the full-app redesign into `develop` only after final validation and zero unresolved blockers.

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

- [ ] All functional/security/quality work merged to `develop` and accepted for Phase 6.
- [ ] Validate production-configured build against real Auth/API.
- [ ] Freeze release versionCode/versionName and promote a release candidate only after Phase 6 gates pass.
- [ ] Document final Android Studio/JDK/SDK prerequisites and clean-clone validation.
- [ ] Perform the first physical-device run.
- [ ] Validate auth → biometric/PIN relaunch → canonical data sync → logout → re-auth on the physical device.
- [ ] Create/preserve a long-lived signing key only when release signing explicitly begins; keep it outside the public repository.
- [ ] Generate a signed APK only when explicitly requested during the Phase 6 handoff.

Do not create a release, production signing key or production-signed APK during the active redesign.
