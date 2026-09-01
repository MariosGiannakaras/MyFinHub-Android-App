# MyFinHub Android status

## 2026-09-01 — Mobile scope narrowed before redesign merge

The active full-app redesign must **not** be merged yet. The user confirmed that the Android product should intentionally omit several desktop/web capabilities and also identified the current branding/icon work as provisional.

### Confirmed Android exclusions

The following are not user-facing Android features unless the user explicitly reverses the decision later:
- Forecast / cash-flow forecast UI and navigation.
- Full desktop-style Reports module. The existing lightweight Android `Insights / Αναλύσεις` destination remains for now and must not silently grow into desktop Reports.
- Category/subcategory administration screens. Category selection remains inside transactions/Quick Entry/budgets/other finance-entry flows where the canonical model requires it.
- Category icon picker / icon administration; Android uses curated static icon mappings.
- Backup / import / Data Transfer UI and navigation.
- Command Palette.
- Desktop keyboard-shortcut UI/workflows.
- Desktop-style mass administration/configuration workspaces.
- Windows install/update/recovery functionality.

These are mobile UI/product-scope decisions only. Android must continue to preserve desktop-owned/unknown canonical finance fields losslessly during read/write round-trips.

### Branding/icon correction required

- The current `MyFinHubIcons` custom drawings are provisional and are not approved as final visual assets.
- Android must use the authentic MyFinHub project artwork for launcher/adaptive icons and brand presentation; no invented replacement logo.
- The final centralized icon language should align with the main product's coherent Lucide-style visual vocabulary while remaining a static Android mapping.
- Screenshot baselines generated before these corrections are not final evidence.

### Immediate implementation order

1. Remove Forecast user-facing card/route/screen references and affected screenshot expectations.
2. Remove Backup/Import/Data Transfer user-facing entry points/routes/screens and affected screenshot expectations.
3. Verify excluded desktop/admin features have no Android entry point.
4. Correct authentic MyFinHub branding/launcher resources.
5. Replace provisional icon drawings with the final centralized icon vocabulary.
6. Re-render and personally inspect real screenshots, then rerun compact/adaptive/150%-font and exact-head validation before merge.

Features not explicitly excluded remain undecided unless already part of the retained Android product. Do not remove more functionality by inference.

## 2026-09-01 — Full-app 2026 UI redesign active

The autonomous implementation remains complete through Phase 5. A new post-Phase-5 UI modernization workstream is active under issue #37 on `feature/full-app-2026-ui-redesign`. This workstream redesigns the **entire retained Android application**, while Phase 6 remains the separate physical-device, production-validation and signing handoff.

### Current redesign direction

- Preserve the authentic MyFinHub branding/logo/product identity.
- Apply the approved 2026 direction across retained Home, Activity, Money, Plan, Insights and retained secondary/detail/auth/system flows.
- Keep the UI compact and modern without becoming packed.
- Build a shared Compose design-system foundation first so colors, typography, spacing, shapes, semantic finance colors, icons and reusable components are centralized rather than duplicated screen by screen.
- Use a curated/static `MyFinHubIcons` system. Android does not need a desktop-style icon picker.
- Finance semantics are intentional and centralized: income green, expense red/coral, savings purple/indigo, transfer blue, attention amber, neutral/info slate/gray.
- Filters and finance rows should use consistent icon + label + semantic color treatment.
- The main MyFinHub desktop/web application is still changing; desktop/web differences are not automatically Android TODO items.
- Issue #24 remains the source of truth for the special native credit-card stack component; the surrounding Money UI may be modernized without silently replacing that contract.
- Memoryless-chat handoff is documented in `docs/UI_2026_REDESIGN_HANDOFF.md` and issue #37.

## 2026-09-01 — Phase 5 autonomous hardening complete

The Android implementation is complete through Phase 5. Phases 0–5 are the completed autonomous implementation scope. Phase 6 remains the separate physical-device, production-validation and signing handoff and is intentionally not part of the completed autonomous scope.

### Current product state before post-Phase-5 scope pruning

- Native Kotlin + Jetpack Compose + Material 3/Adaptive application with Navigation 3 and ViewModel/StateFlow/UDF.
- Five top-level destinations remain Home, Activity, Money, Plan and Insights; detailed workflows stay nested.
- The production launcher remains auth-gated; debug product/auth test hosts are debug-only and non-exported.

### Canonical data and security state

- Production auth/session, AAL2 validation, biometric-first local unlock and PIN fallback are implemented.
- Canonical `/api/data` bearer/revision sync remains server-authoritative and revision-aware in memory; Android does not introduce a canonical Room/SQLite database.
- Backup/import bearer client boundaries and PAN/expiry `/api/card-secrets` boundaries were completed through PR #21. Backup/import is now excluded as a user-facing Android feature, but compatibility-only code may remain if needed for safe canonical behavior.
- Card-detail PAN/expiry reveal is owner+AAL2 gated.
- CVV is device-local Android Keystore AES-GCM data only and has no server, sync, log, backup or device-transfer path.
- Secret reveal is protected by scoped secure-window/recent-thumbnail handling and clears on navigation/session/auth transitions.
- No service-role/server-vault secret, signing password, production signing key or production-signed APK belongs in the completed autonomous workflow.

### Phase 5 completed work and final evidence

- Sensitive log/source/preview audit.
- Representative recomposition and large-state review.
- Release R8/minification/resource shrinking with narrow keep rules.
- Unsigned release/configuration validation; public CI creates no production-signed APK and requires no signing secret.
- Device-generated Baseline Profile and startup profile are checked in and non-empty; final verification is read-only.
- Benchmark/non-minified profiling hosts and a dedicated Macrobenchmark/Baseline Profile producer module are in place.
- Exact-head Android CI passed benchmark/profile tooling build, unit and instrumentation compilation, lint, debug assembly, optimized unsigned release/R8 analysis, and packaged-manifest/unsigned-APK policy checks.
- Exact-head screenshot regression and compact instrumentation passed.
- Exact-head full-product Pixel Fold, Pixel Tablet and 150% font-scale/accessibility suites passed.
- Exact-head Macrobenchmarks passed for cold startup, Home, Activity and Quick Entry.
- Exact-head read-only Baseline Profile generation/verification passed on the targeted retry after an emulator-launch instability on the immediately preceding no-code-change attempt.
- PR #36 is merged into `develop`.

### Phase 6 boundary

Phase 6 remains intentionally deferred and separate. It includes the first physical-device run, production Auth/API smoke validation, final release-candidate promotion and any signing-key/signed-APK work. No release, production signing key or production-signed APK was created during Phase 5, and the active UI redesign must keep this boundary unchanged.
