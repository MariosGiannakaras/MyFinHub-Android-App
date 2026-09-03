# MyFinHub Android status

## 2026-09-03 — Phase 6 physical-device correction pass complete and merged

The first physical Samsung Galaxy S24 Ultra run exposed a focused set of correctness and product-UX issues. Tracker #50 is completed/closed and its validated correction PR was squash-merged into `develop`.

### Completed correction work

- Production finance writes now honor the durable-history generation precondition together with revision concurrency metadata, and mutation eligibility requires a consistent finance/history snapshot.
- Card deletion was corrected across canonical finance state, server PAN/expiry storage and device-local encrypted CVV handling; a failed/refused mutation no longer leaves a misleading local deletion.
- Duplicate operational save-failure presentation was removed.
- Appearance now persists System / Light / Dark instead of forcing dark mode.
- Home prioritizes the three deterministic primary everyday accounts and recent canonical activity before secondary information.
- Quick Entry is optimized around amount-first common entry with compact controls, sensible defaults and progressively disclosed secondary fields.
- Settings prioritizes real user preferences/account actions while keeping safe diagnostics secondary.
- Plan distinguishes obligations, expected income and transfers; Insights removes engineering-oriented hierarchy/copy and prioritizes financial summary content.

### Screenshot and hosted validation

- Changed Home, Quick Entry, Settings and Plan light/dark/150%-font renders were generated as real Compose screenshots and personally inspected before acceptance.
- A Home large-font wrapping defect was found during inspection and corrected before canonicalization.
- Only the latest validated screenshot references/review images are retained for the changed surfaces.
- Final canonical screenshot regression passed on the accepted references.
- Final representative S24-target instrumentation passed.
- Final normal Android verification passed: benchmark/Baseline Profile tooling, unit tests, instrumentation compile, lint/debug assembly, optimized unsigned release/R8 and release-manifest/unsigned-APK policy audit.
- Zero unresolved correction-PR review blockers remained at merge.

### Current Phase 6 boundary

All autonomous repository corrections from the first physical run are complete. The current `develop` state is the authoritative build for the next physical Galaxy S24 Ultra acceptance pass.

The physical device must still revalidate production Auth/API, one reversible mutation and reload, offline/reconnect behavior, real card deletion, owner+AAL2 card-secret behavior, appearance persistence, Samsung One UI/display/font rendering and device-specific performance. `docs/PHASE_6_DEVICE_HANDOFF.md` and issue #14 remain authoritative.

No `develop -> main` promotion, release/version freeze, production signing key, production-signed APK or release has been performed or authorized.

## 2026-09-03 — Design System & Pixel-Spec hardening complete and merged

Tracker #47 is completed/closed. PR #48 was validated on the exact accepted implementation/reference head and squash-merged into `develop`.

### Completed visual-system hardening

- Added explicit executable MyFinHub geometry, typography and shape contracts for screen edges, cards, rows, icons, touch targets, form fields, primary actions, auth layout, navigation clearances and secure Card Detail alignment.
- Shared finance actions and custom interactive targets now use a 48dp minimum; shared outlined fields/selectors use a 56dp minimum with documented 1dp/2dp border behavior.
- Light and dark semantic finance palettes are regression-tested against the documented >=4.5:1 normal-text target; essential outlines are tested against the >=3:1 graphical threshold.
- Quick Entry, Activity detail, canonical Plan budgets, Settings/History, Home quick-entry choices and Auth Login/TOTP/PIN/biometric fallback use the shared visual primitives where product semantics require MyFinHub ownership.
- Material 3 geometry/state behavior remains intentionally inherited where framework defaults already satisfy the product instead of duplicating framework internals.
- Production-only canonical secure Card Detail now follows the shared component system while preserving the owner+AAL2 PAN/expiry boundary, `SecureWindowProtection` and the device-local Keystore CVV boundary.
- Repeated 96dp content and 152dp Snackbar clearances are expressed as named relationships derived from persistent navigation/action geometry rather than unexplained screen literals.

### Final visual and hosted validation

- Canonical screenshot references total 41 real Compose images.
- The final accepted implementation was rendered before canonicalization; previously approved surfaces remained accepted and three sanitized Card Detail states were personally inspected: hidden light, hidden dark and revealed at 150% font scale.
- Screenshot fixtures/references contain no reusable PAN/CVV secret.
- Final screenshot regression passed with candidate regeneration skipped.
- Representative S24-target instrumentation completed successfully, including the interaction/accessibility step.
- Normal Android CI completed successfully: benchmark/Baseline Profile tooling, unit tests, instrumentation compile, lint/debug assembly, optimized unsigned release/R8 and release-manifest/unsigned-APK policy audit.
- Zero unresolved review threads remained at merge.

### Preserved Phase 6 boundary

- Hosted emulator validation is representative only; the owner's physical Samsung Galaxy S24 Ultra remains authoritative.
- Production-configured Auth/API, Samsung One UI/display/font rendering, real reconnect/mutation/card-secret behavior and physical-device performance remain Phase 6.
- No production signing key, production-signed APK or release was created.
- `docs/PHASE_6_DEVICE_HANDOFF.md` and issue #14 remain authoritative for the physical-device/signing handoff.

## 2026-09-02 — UI/UX hardening complete and merged

The pre-Phase-6 Android UI/UX hardening pass is complete and merged into `develop`. Draft PR #45 was closed without code changes only because the connected GitHub Mark-ready GraphQL operation failed on its own unsupported schema field. Ready PR #46 used the exact same validated branch/head and was squash-merged into `develop`. Do not reopen this workstream for repeat discovery.

### Completed product hardening

- Home quick-entry now enters the real canonical Quick Entry flow with the chosen transaction type preselected; the duplicate compact promotion was removed after the primary flow became trustworthy.
- Logout moved from the persistent finance-screen overlay into Settings/account actions.
- Production-looking synthetic Change History data was removed.
- Money and Plan demo fixtures are isolated from production canonical projection; production routes show only synchronized data and only expose edits backed by canonical mutations.
- Quick Entry received mobile-focused input hardening: monetary keyboards, Material date selection, field-level validation, first-invalid-field focus/scroll behavior and safer compact split editing.
- State-specific recovery copy distinguishes revision conflict, offline waiting and failed save conditions while preserving permanent issue #27 no-blind-write-retry semantics.
- Compact Home hierarchy was simplified and low-frequency utility controls were removed from the primary financial content stream.
- Compose accessibility checks were added; spoken labels and undersized touch targets found during runtime validation were corrected.
- Compact, dark and large-font screenshot coverage was updated for the changed product surfaces. Obsolete curated Money/Plan editor screenshots that no longer represent production routes were removed/replaced.

### Final validation and visual evidence

- Real Compose screenshot candidates for the changed Home, Quick Entry, Change History, canonical Money and canonical Plan surfaces were personally inspected before acceptance.
- The committed canonical screenshot references then passed regression without regeneration.
- Representative S24-target instrumentation passed on the exact validated implementation head, including the accessibility checks.
- Normal Android CI passed on the same head: benchmark/Baseline Profile tooling build, unit tests, instrumentation compile, lint, debug assembly, optimized unsigned release/R8 analysis and release-manifest/unsigned-APK policy audit.
- Zero unresolved review threads and zero hosted implementation blockers remained at merge.

### Preserved Phase 6 boundary

- Samsung Galaxy S24 Ultra remains the sole supported Android target; hosted emulator instrumentation is representative only.
- Real production Auth/API, Samsung One UI/display/font rendering and device-specific startup/performance acceptance remain Phase 6.
- No release, production signing key or production-signed APK was created in this workstream.
- `docs/PHASE_6_DEVICE_HANDOFF.md` and issue #14 remain authoritative for the final physical-device/signing handoff.

## 2026-09-02 — Full transaction entry parity complete and merged

Tracker #42 is completed/closed. The initial draft PR #43 was closed without code changes only because the available connector's Mark-ready GraphQL operation failed on its own schema field. Ready PR #44 used the exact same validated branch/head and was squash-merged into `develop`. Do not reopen this workstream for repeat discovery.

### Canonical transaction-entry parity

- Android now supports the full retained transaction model: Expense, Income, Transfer, Withdrawal, Saving cash offset, Refund, Lending, Repayment, Card purchase, Card payment, Reconciliation and canonical Split.
- Transaction type selection reveals only fields relevant to that accounting intent. Date, account, card, person, category and subcategory choices are explicit where required instead of silently choosing hidden values.
- Account/card options and expense/income category trees are projected from the real canonical document. Unknown desktop-owned fields remain losslessly preserved by the existing canonical mutation layer.
- Withdrawal is constrained to a cash destination and saving cash offset to a savings destination at the canonical mutation boundary, not only in UI filtering.
- Lending and repayment carry per-person receivable semantics. Repayment is rejected when the named person has no outstanding balance or when the amount exceeds that person's canonical outstanding debt.
- Card purchase/payment use an explicit active credit-card identity. Card payment is rejected when the selected card has no debt or when the amount exceeds its current canonical debt; when canonical bank identity is available the payment account must belong to the same bank, matching desktop behavior.
- Reconciliation records only the difference between the entered real balance and the calculated canonical account balance.
- Split is a true accounting split: each part has its own amount/category/subcategory/label and the parent amount is derived exactly from the parts. The previous people-count split behavior is no longer canonical transaction-entry behavior.
- Dirty transaction drafts require explicit discard confirmation on back navigation.

### Final validation and visual evidence

- Domain/mutation tests cover ledger effects, destination constraints, category/subcategory projection, reconciliation deltas, split cent totals, per-person lending debt and per-card debt/overpayment boundaries.
- Representative compact-device instrumentation covers full transaction-entry navigation, dynamic type fields, the scrollable split editor and dirty-draft discard protection.
- Real Compose candidates for the updated compact transaction form and the new split form were personally inspected. Both were accepted: no clipping/overlap defect was found, and the split editor's continuation below the viewport is intentional scroll behavior.
- A later renderer run from the debt-validation source state produced pixel-identical candidates, and exactly those accepted PNGs became the canonical screenshot references.
- The exact final human-authored implementation state passed normal Android CI: benchmark/Baseline Profile tooling build, unit tests, instrumentation compile, lint, debug assembly, optimized unsigned release/R8 analysis and release-manifest/unsigned-APK policy audit.
- The same exact final implementation state passed canonical screenshot regression and representative S24-target instrumentation.
- Zero unresolved review threads and zero supported-device implementation blockers remained at merge.

### Preserved boundaries

- Existing issue #27 revision-conflict, offline and write-retry rules remain unchanged: attempted writes are not blindly retried after ambiguous transport failure, and reconnect replay is limited to mutations known never to have been sent after a fresh server reload.
- Samsung Galaxy S24 Ultra remains the sole supported Android target; hosted emulator instrumentation is representative only and does not replace Phase 6 physical Samsung acceptance.
- The separately excluded additional privacy/security audit package remains out of scope.
- No release, production signing key or production-signed APK was created in this workstream.

## 2026-09-02 — Post-redesign resilience, data-integrity and diagnostics hardening complete and merged

The retained native Android product has completed and merged the autonomous resilience/data-integrity/auth-recovery/edge-state/diagnostics work requested after the full-app 2026 redesign. The sole supported Android target remains the owner's **Samsung Galaxy S24 Ultra**; `docs/SUPPORTED_DEVICE.md` remains the permanent device-acceptance contract.

Tracker #39 is complete. The validated implementation was originally reviewed in draft PR #40; because the available connector could not transition that draft out of draft state, #40 was closed without code changes and replaced by ready PR #41 from the exact same validated branch/head. PR #41 was squash-merged into `develop`. Do not reopen this workstream for repeat discovery.

### Completed resilience and integrity work

- Production HTTP policy now has explicit connect/read/write/call timeouts and hidden OkHttp connection retries disabled.
- Safe finance reads use a bounded explicit retry for transient failures; attempted writes are never blindly replayed after an ambiguous transport result.
- Android connectivity has explicit online/offline/unknown state. A mutation can remain pending only when the app knows no write request was sent; reconnect reloads the newest server state before replaying the stable mutation intent.
- Rapid duplicate finance submissions, login/TOTP actions, card-secret operations and duplicate nested navigation pushes are suppressed.
- Finance work is cancelled when auth/user state is cleared or switched.
- Canonical integrity validation rejects malformed known collections, duplicate stable IDs, invalid known dates/months, non-finite/out-of-range money values and invalid revision shapes before server data becomes product state.
- Unknown/desktop-owned canonical fields remain preserved losslessly; empty/first-use canonical data remains valid.
- Transient/offline local-unlock validation retains the recoverable session in a secure locked state. Expired/revoked auth still requires login.
- Explicit logout always clears the encrypted local session boundary even if remote revoke unexpectedly fails.

### S24 Ultra UX and safe diagnostics

- Offline/pending finance recovery has a distinct user-facing state instead of a generic save failure.
- Settings includes safe in-app diagnostics for app/build, public environment/API host, connectivity, API/sync state, session/AAL state, last successful sync and the latest safe diagnostic code.
- Diagnostics never include credentials, access/refresh tokens, user IDs, finance payloads, account/transaction content, PAN, expiry or CVV.
- Empty/first-use data, rapid navigation/actions, long account labels and large positive/negative amounts have dedicated automated coverage for the 412×915 phone target.
- Real Compose screenshot candidates for Diagnostics offline/recovery and Home edge values were personally inspected. The first Home edge fixture was rejected because test-only oversized attention text created an ambiguous FAB overlap; the fixture was corrected and only the clean replacement was accepted as the canonical reference.
- Remaining unrouted Backup/Import/Data Transfer utility remnants were removed; those product surfaces remain intentionally excluded from Android.

### Final validation

The final implementation state passed twice on the same code state, including the replacement ready PR event:

1. normal Android CI: benchmark/Baseline Profile tooling build, unit tests, instrumentation compile, lint, debug assembly, optimized unsigned release/R8 analysis and release-manifest/unsigned-APK policy audit;
2. canonical screenshot regression including the two personally approved new phone references;
3. representative S24 Ultra-target compact-phone instrumentation;
4. zero unresolved review threads and zero supported-device implementation blocker.

The stale Phase 5 draft PR #23 was also closed as superseded by the already completed Phase 5 PR #36, so there is no leftover autonomous Android implementation PR from an older workstream.

### Explicit exclusion

The separately proposed additional privacy/security audit package (clipboard/recent-app/accessibility-secret audit) was intentionally **not implemented**, per product-owner instruction. Existing security boundaries remain in force.

## Phase 6 boundary

`docs/PHASE_6_DEVICE_HANDOFF.md` contains the prepared physical-device checklist and workstation prerequisites. Issue #14 remains the authoritative Phase 6 tracker.

The owner's physical Samsung Galaxy S24 Ultra is required for real production Auth/API validation, Samsung One UI/display/font rendering, device-specific performance and the final release-candidate decision. Signing starts only after that acceptance and explicit product-owner authorization.

Do not create a release, production signing key or production-signed APK before the explicit Phase 6 signing handoff.
