# MyFinHub Android status

## 2026-09-01 — Full-app 2026 redesign in final S24 Ultra validation

The retained Android product has been migrated to the shared 2026 visual system on the active redesign workstream. The sole supported device is now explicitly the owner's **Samsung Galaxy S24 Ultra**; see `docs/SUPPORTED_DEVICE.md`.

The redesign is not merged yet only because the final exact-state phone/CI validation and tracker synchronization must complete first.

### Completed in the redesign

- Shared light/dark theme, typography, shapes, compact spacing and semantic finance palette.
- Shared headers, cards, finance rows, search/filter treatments, amount styles, icon badges and actions.
- Central curated/static `MyFinHubIcons` vocabulary aligned with the main product's Lucide-style language; feature screens no longer depend on provisional one-off icon drawings.
- Authentic MyFinHub light/dark brand artwork imported from the central product and used by the shared Android branding contract, including launcher/adaptive resources.
- Home, Activity, Money, Plan, Insights, Quick Entry, auth, Home attention detail, Settings, Change History, Savings, Loans, Lending, Plan item/editor and Budgets/rules migrated to the shared visual system.
- Forecast user-facing UI/navigation/screens removed from Android.
- Backup/Import/Data Transfer user-facing UI/navigation/screens removed from Android.
- Confirmed Android has no category/subcategory administration, category icon picker, Command Palette, desktop keyboard-shortcut workflow, desktop mass-administration/configuration workspace, Windows install/update/recovery UI or full desktop Reports module. Lightweight Android Insights remains.
- Canonical/desktop-owned finance fields remain losslessly preserved even when Android exposes no corresponding UI.
- Real screenshot references were regenerated after branding/icon/scope correction and personally inspected.
- Instrumented UI tests were aligned with the redesigned accessibility/search semantics instead of relying on stale visible-text selectors.
- Personal inspection found one expanded-Home collision between the global quick-entry action and the in-card quick-entry action; expanded Home now keeps only the unobstructed in-card primary action while compact Home retains the floating action.
- The updated expanded Home screenshot reference was rendered, replaced and personally inspected; the overlap is gone.
- The canonical screenshot suite passed after the Home reference replacement.
- The supported-device contract was narrowed by user decision to the Samsung Galaxy S24 Ultra only.
- Automatic tablet/foldable/150%-font adaptive CI matrices were removed because those device classes/settings are not acceptance targets for this single-device app.
- Hosted-emulator Baseline Profile/Macrobenchmark workflows remain available manually as diagnostics, while normal CI still verifies benchmark/profile tooling compilation. Device-specific performance acceptance belongs to the physical S24 Ultra handoff.

### Post-review reliability and cleanup hardening

- Operational failures now use a shared `UserNotice` contract across auth/session, canonical finance sync and secure card-secret flows.
- User-facing operation/system failures are surfaced through a global Material 3 Snackbar with a `Λεπτομέρειες` action; field-level validation remains inline next to the relevant input instead of producing duplicate transient messages.
- Error details expose only safe diagnostics such as operation, failure category, HTTP status, retryability and a diagnostic code. Raw server bodies, exception messages, credentials, access tokens, PAN and CVV are not exposed.
- Network/auth parsing and malformed-success-response edge cases are contained inside typed failure results instead of escaping as uncaught exceptions.
- Repository boundaries defensively convert unexpected API implementation failures into recoverable failures while preserving coroutine cancellation semantics.
- Failed local finance mutations/projections retain the last valid product state instead of collapsing the whole UI into a fatal screen; sync conflicts/save failures remain explicitly retryable/discardable.
- Secure local CVV vault read/save/delete failures are no longer silently ignored and are reported without leaking card secrets.
- Obsolete Phase-1 Bootstrap scaffolding and obsolete Android Backup/Import API surface that no longer belongs to the retained product were removed; the standalone synthetic Home state holder was retained and documented because it is still used by test/demo UI infrastructure.
- Unsupported expanded/tablet/foldable screenshot cases and references were removed from the S24 Ultra-only visual acceptance suite.
- Dedicated phone screenshot evidence was added for the global error Snackbar and safe details dialog. Personal inspection caught and corrected both asynchronous preview timing and bottom-navigation overlap before accepting a new reference.

### Final merge gates

1. Pass normal Android verification on the final exact state.
2. Pass real screenshot regression on the final exact state.
3. Pass one representative compact-phone instrumentation suite for the S24 Ultra phone target.
4. Synchronize issue #37 / PR #38 completion state and merge into `develop` with no unresolved S24 Ultra blocker.

Tablet, foldable and other form-factor failures are not blockers unless the same defect affects the Galaxy S24 Ultra.

### Confirmed Android exclusions

The following are intentionally not user-facing Android features unless explicitly reversed later:
- Forecast / cash-flow forecast.
- Full desktop-style Reports module; lightweight `Insights / Αναλύσεις` remains.
- Category/subcategory administration.
- Category icon picker / icon administration.
- Backup / Import / Data Transfer UI.
- Command Palette.
- Desktop keyboard-shortcut UI/workflows.
- Desktop-style mass administration/configuration workspaces.
- Windows install/update/recovery functionality.

These exclusions do not permit Android to strip unknown or desktop-owned canonical finance fields.

## Phases 0–5

The autonomous Android implementation through Phase 5 is complete and merged into `develop`. It includes the native Compose product, auth/local unlock, canonical finance integration, owner+AAL2 card-secret boundaries, device-local CVV vault, secure-window handling, R8/minification/resource shrinking, Baseline Profile/startup profile tooling, Macrobenchmarks and pre-redesign validation evidence.

No service-role/server-vault secret, production signing key or production-signed APK belongs in the completed autonomous workflow.

## Phase 6 boundary

Phase 6 remains intentionally separate and untouched by this redesign. The owner's physical Samsung Galaxy S24 Ultra is the authoritative device for the first real-device run, actual Samsung One UI/display/font configuration, production Auth/API smoke validation, device-specific performance acceptance, release-candidate promotion and eventual signing work.

Do not create a release, production signing key or production-signed APK during the redesign merge.
