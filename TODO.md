# MyFinHub Android TODO

## Phase 6 offline-first physical correction — completed and merged

Tracker #54 / correction PR are complete and merged into `develop`.

- [x] Persist the last server-accepted canonical finance snapshot locally with owner-scoped Android Keystore encryption.
- [x] Restore cached finance data after process death/relaunch for authenticated local unlock.
- [x] Persist multiple offline-created transactions durably instead of blocking after one pending mutation.
- [x] Keep offline navigation usable and represent pending Activity items non-blockingly.
- [x] Reload fresh server state before replay and reconcile stable event IDs to prevent duplicates.
- [x] Automatically replay only transactions known never to have been sent; ambiguous attempts require explicit review/retry.
- [x] Keep successful PIN/biometric local unlock cache-capable during transient network/server session-validation failures; revoked/expired sessions still require login.
- [x] Add canonical synchronized transaction deletion plus safe local cancellation for never-synced pending transactions.
- [x] Cover cache restore, multi-pending persistence, replay idempotency, server-first reconciliation, ambiguous retry exclusion and deletion semantics with tests.
- [x] Accept final real Compose Activity pending light/dark/150%/detail references after personal inspection.
- [x] Final canonical screenshot regression, representative S24 instrumentation and complete normal Android verification green.
- [x] Merge with no unresolved hosted review blocker.

Physical S24 validation remains open in issue #14: offline cold/local unlock, multi-pending restart, reconnect/no-duplicate, ambiguous-write recovery, local pending cancellation and synchronized deletion/reload must be confirmed on the owner's Samsung Galaxy S24 Ultra.

## Phases 0–5 — completed

- [x] Research/design foundation and repository workflow.
- [x] Kotlin/Compose native product and retained mobile UX.
- [x] Production native-client backend gate and canonical finance integration.
- [x] Native auth, AAL2, biometric/PIN local unlock and encrypted session persistence.
- [x] Owner+AAL2 PAN/expiry boundary and device-local Keystore CVV vault.
- [x] Security/performance/release hardening through Phase 5, including R8, Baseline Profile/startup-profile and Macrobenchmark infrastructure/evidence.

## Full-app 2026 redesign + first reliability hardening — completed

Tracker #37 / PR #38 completed and merged into `develop`.

- [x] Shared 2026 visual system and redesign of the retained Android product.
- [x] Samsung Galaxy S24 Ultra is the sole supported target; tablet/foldable/desktop-like acceptance removed.
- [x] Forecast and Backup/Import/Data Transfer user-facing Android scope removed while canonical compatibility remains lossless.
- [x] Safe operational `UserNotice` + Snackbar/details behavior merged.
- [x] Typed network/auth failures, recoverable finance/CVV failures and cancellation semantics merged.

## Post-redesign resilience, data integrity & diagnostics — completed and merged

Tracker #39 is complete. Draft PR #40 was closed only because the connector could not transition it out of draft; ready PR #41 used the exact same validated branch/head and was squash-merged into `develop`.

### Network / offline resilience

- [x] Explicit production HTTP timeouts.
- [x] Hidden OkHttp connection retries disabled so write retry policy remains application-owned.
- [x] Bounded retry for safe finance reads on transient NETWORK/SERVER failures.
- [x] No automatic retry of an attempted finance write after an ambiguous transport result.
- [x] Validated Android connectivity state: online/offline/unknown.
- [x] Offline load preflight with reconnect retry only when no request started.
- [x] Pending finance mutation retained only when the write is known never to have been sent.
- [x] Reconnect recovery reloads current server state before replaying stable mutation intent.
- [x] Rapid duplicate finance submissions blocked before coroutine state races can create a second write.
- [x] Repository tests verify transient read recovery and single-attempt transport-failed writes.

### Canonical data integrity

- [x] Reject malformed known canonical collection structures before product projection.
- [x] Detect duplicate stable IDs in known canonical identity collections.
- [x] Validate known dates/months and finite bounded money values.
- [x] Validate loaded/saved revision shape.
- [x] Preserve unknown/desktop-owned canonical fields losslessly through Android mutations.
- [x] Keep empty datasets valid and cover first-use product projection.
- [x] Preserve mutation intent on revision conflicts without silently overwriting newer server state.

### Auth/session recovery

- [x] Offline login/TOTP preflight without retaining credentials for automatic retry.
- [x] Synchronous Loading transition prevents rapid duplicate auth requests.
- [x] Transient/offline local-unlock validation retains the recoverable session without forcing login; cache-capable product entry is completed by the later Phase 6 offline-first correction above.
- [x] Unauthorized/expired sessions still clear stored session and require login.
- [x] Explicit logout always clears the encrypted local session boundary even when remote revoke throws.
- [x] Finance jobs are cancelled when auth/user state is cleared or switched.
- [x] Rapid card-secret reveal/save/delete operations are suppressed.

### S24 Ultra UX edge states

- [x] Empty/first-use canonical data projection covered.
- [x] Rapid duplicate nested-route pushes suppressed.
- [x] Offline/pending finance save has a distinct recoverable UI issue.
- [x] Long account labels and very large positive/negative values covered by a 412×915 Home visual fixture.
- [x] Diagnostics offline/recovery covered by a 412×915 visual fixture.
- [x] Both new rendered candidates personally inspected; ambiguous overlap candidate rejected and corrected before canonical acceptance.
- [x] Device-specific network-toggle/Samsung behavior left correctly for the physical S24 Ultra Phase 6 smoke sequence rather than pretending the hosted representative emulator is exact Samsung validation.

### Safe in-app diagnostics

- [x] Safe diagnostics snapshot contract.
- [x] App version/build type, public environment/API host, connectivity, API state, session/AAL state, last successful sync and safe diagnostic code surfaced in Settings.
- [x] Tokens, credentials, user IDs, PAN/CVV and finance payloads excluded from diagnostics.
- [x] Latest safe diagnostic code captured from the existing notice stream.
- [x] Canonical diagnostics phone screenshot accepted after personal visual inspection.

### Cleanup / Phase 6 preparation

- [x] Remove remaining unrouted/dead Backup/Import/Data Transfer utilities state/screen/test remnants.
- [x] Add `docs/PHASE_6_DEVICE_HANDOFF.md` with physical-S24 smoke flow and clean-clone/workstation prerequisites.
- [x] Keep release/signing work out of this autonomous workstream.
- [x] Final normal CI, screenshot regression and representative S24-target instrumentation green on the merged implementation state.
- [x] Stale Phase 5 draft PR #23 closed as superseded by the already completed Phase 5 PR #36.
- [x] Merge completed with zero supported-device blocker; permanent issue #27, tracker #39 and Phase 6 issue #14 synchronized afterward.

### Explicit exclusion

The additional privacy/security audit proposal (clipboard/recent-app/accessibility-secret audit package) is intentionally **not** implemented in this workstream, per product-owner instruction.

## Full transaction entry parity — completed and merged

Tracker #42 is completed/closed. Draft PR #43 was closed without code changes only because the connector's Mark-ready GraphQL operation failed; ready PR #44 used the exact same validated branch/head and was squash-merged into `develop`.

- [x] Replace the four-kind Quick Entry prototype with all 12 retained canonical transaction kinds.
- [x] Use a native mobile type-first flow with dynamic fields instead of copying the desktop layout.
- [x] Add explicit transaction date and account/card/person fields only where the selected kind requires them.
- [x] Project account/card choices from the real canonical document rather than hidden synthetic defaults.
- [x] Replace free-text category entry with canonical expense/income category and subcategory choices.
- [x] Keep transfer source/destination explicit and prevent same-account transfers.
- [x] Enforce withdrawal-to-cash and saving-to-savings destinations in the canonical mutation boundary.
- [x] Implement lending and repayment with canonical per-person receivable deltas and expected-return-date validation.
- [x] Reject repayment with no outstanding person debt or an amount above that person's outstanding debt.
- [x] Implement explicit credit-card purchase/payment accounting against the shared credit liability.
- [x] Reject credit-card payment with no selected-card debt or an amount above that card's canonical debt; enforce same-bank source account when canonical bank identity is available.
- [x] Implement reconciliation as actual balance minus calculated canonical balance without creating artificial income/expense flow.
- [x] Replace people-count split with real accounting split parts; derive the parent amount exactly from part amounts and preserve per-part category/subcategory/label.
- [x] Preserve issue #27 revision-conflict, offline and attempted-write retry rules without introducing hidden write retries.
- [x] Require explicit confirmation before discarding a dirty transaction draft.
- [x] Add reducer/domain/mutation/projection coverage for the full transaction-entry contract and debt-limit rules.
- [x] Add representative compact-device instrumentation for full navigation, dynamic fields, scrollable split editing and dirty-draft discard behavior.
- [x] Render real Compose candidates for the changed transaction form and new split form, personally inspect them, and accept only the clean references.
- [x] Confirm the later debt-validation source state renders pixel-identical accepted transaction-entry candidates before canonicalizing them.
- [x] Exact final Android CI, canonical screenshot regression and representative S24-target instrumentation green.
- [x] Zero unresolved review threads and zero supported-device implementation blockers at merge.
- [x] Keep the S24 Ultra as the sole supported target and leave physical Samsung-specific acceptance to Phase 6.
- [x] Keep release/signing and the explicitly excluded additional privacy/security audit out of this workstream.
- [x] Synchronize tracker #42, permanent issue #27 and Phase 6 issue #14 after merge.

## Pre-Phase-6 UI/UX hardening — completed and merged

Draft PR #45 was closed without code changes only because the connected GitHub Mark-ready GraphQL operation failed on its own schema field. Ready PR #46 used the exact same validated branch/head and was squash-merged into `develop`.

- [x] Repair Home quick-entry routing and preload the selected canonical transaction type.
- [x] Remove duplicate compact Home quick-entry promotion after the primary flow became functional.
- [x] Move logout into Settings/account actions.
- [x] Remove synthetic production-looking Change History records.
- [x] Isolate Money/Plan demo fixtures from production canonical projection.
- [x] Use production-facing canonical Money/Plan screens that display only synchronized data and only expose mutation-backed edits.
- [x] Add mobile-friendly money keyboards and Material date selection to Quick Entry.
- [x] Add field-level validation and first-invalid-field focus/scroll behavior.
- [x] Improve compact split-entry editing and preserve existing canonical accounting rules.
- [x] Add action-specific revision-conflict/offline/save-failure recovery wording without changing issue #27 write-retry semantics.
- [x] Simplify compact Home information hierarchy and reduce low-priority utility/card prominence.
- [x] Add Compose accessibility checks and fix discovered spoken-label/touch-target defects.
- [x] Expand compact, dark and large-font screenshot coverage for changed product surfaces.
- [x] Personally inspect new real Compose candidates and replace obsolete canonical references rather than accumulating alternatives.
- [x] Final committed screenshot references pass regression without regeneration.
- [x] Final representative S24-target instrumentation passes, including accessibility checks.
- [x] Final normal Android CI passes benchmark/Baseline Profile tooling, tests, lint, debug assembly, optimized unsigned release/R8 and manifest/signing-policy audit.
- [x] Zero unresolved review threads and zero hosted implementation blockers at merge.
- [x] Keep production Auth/API, Samsung-specific rendering/performance and signing/release exclusively in Phase 6.

## Design System & Pixel-Spec hardening — completed and merged

Tracker #47 is completed/closed. PR #48 was validated on the exact accepted implementation/reference head and squash-merged into `develop`.

- [x] Audit and document spacing, typography, shape roles, borders, icon sizes and interactive touch targets.
- [x] Add executable MyFinHub geometry, typography and shape contracts.
- [x] Audit light/dark semantic finance contrast and enforce documented text/graphics thresholds with tests.
- [x] Define explicit component contracts for actions, icon buttons, form fields/selectors, cards/rows, headers, navigation, FAB, sheets/dialogs/snackbars and switches, with intentional Material 3 inheritance recorded where applicable.
- [x] Replace product-owned ad-hoc control dimensions and repeated navigation/action clearances with shared MyFinHub metrics/relationships.
- [x] Migrate Quick Entry, Activity detail, canonical Plan budgets, Settings/History, Home quick-entry controls and Auth Login/TOTP/PIN/biometric fallback to shared controls where appropriate.
- [x] Align the production secure Card Detail/CVV surface with shared pixel-spec controls while preserving owner+AAL2, secure-window and device-local vault boundaries.
- [x] Complete compact screen-rhythm cleanup for Home/Activity/system states without changing finance behavior.
- [x] Preserve truthful production content and remove obsolete synthetic-facing Insights wording.
- [x] Render and personally inspect the final real Compose candidate set.
- [x] Accept 41 canonical references, including sanitized Card Detail hidden light/dark and 150%-font revealed states with no reusable PAN/CVV secret.
- [x] Final screenshot regression passes with candidate regeneration skipped.
- [x] Final representative S24-target instrumentation passes the interaction/accessibility suite.
- [x] Final normal Android CI passes benchmark/Baseline Profile tooling, unit/instrumentation compile, lint/debug assembly, optimized unsigned release/R8 and release-policy audit.
- [x] Zero unresolved review threads at merge.
- [x] No production signing key, signed APK or release created; physical Samsung acceptance remains Phase 6.

## Phase 6 physical-device correction pass — completed and merged

Tracker #50 is completed/closed. The correction PR was validated and squash-merged into `develop` after the first physical Galaxy S24 Ultra findings.

- [x] Align production finance writes with durable history generation plus revision concurrency preconditions.
- [x] Require a consistent finance + durable-history read snapshot before mutation.
- [x] Fix card deletion across canonical state, server PAN/expiry storage and device-local CVV cleanup with regression coverage.
- [x] Ensure failed/refused card deletion cannot leave a ghost local deletion.
- [x] Remove duplicate modal/snackbar presentation for the same operational save failure.
- [x] Persist Appearance as System / Light / Dark; do not force dark mode.
- [x] Rebuild Home around deterministic primary everyday accounts and recent canonical activity.
- [x] Streamline Quick Entry around amount-first common entry with compact controls and progressive disclosure.
- [x] Simplify Settings so real preferences/account actions precede collapsed safe diagnostics.
- [x] Distinguish Plan obligations, expected income and transfers; clean up Insights hierarchy/copy.
- [x] Render real Compose Home / Quick Entry / Settings / Plan light, dark and 150%-font candidates.
- [x] Personally inspect the changed candidates, fix the discovered Home large-font wrapping defect and canonicalize only the clean replacements.
- [x] Final canonical screenshot regression green.
- [x] Final representative S24-target instrumentation green.
- [x] Final normal Android verification green: benchmark/Baseline Profile tooling, unit tests, instrumentation compile, lint/debug assembly, optimized unsigned release/R8 and release-manifest/unsigned-APK policy audit.
- [x] Zero unresolved correction-PR review blockers at merge.
- [x] Synchronize tracker #50, permanent issue #27, Phase 6 issue #14, `STATUS.md` and `TODO.md` after merge.

## Private self-updater — completed and merged

Tracker #52 is completed/closed and the validated updater implementation is merged into `develop`.

- [x] Add automatic and manual authenticated owner+AAL2 update checks without blocking normal product use when the update service is unavailable.
- [x] Add a production Settings Updates section with current version, release state/notes, progress and install/recovery actions.
- [x] Download APKs only from the configured private bearer-authenticated `android-releases` Storage path with bounded streaming, exact-size and SHA-256 validation.
- [x] Reject malformed metadata, insecure/wrong-host URLs, downgrade/wrong version, wrong package, wrong signer and integrity mismatch.
- [x] Add PackageInstaller integration with install-source permission handoff and system-confirmation fallback; do not bypass Android security prompts.
- [x] Preserve updater failures outside auth-logout/finance failure policy and preserve encrypted session/PIN/CVV application data across normal package replacement.
- [x] Align with the central MyFinHub owner+AAL2 `/api/android-update` endpoint and private Storage/metadata RLS already merged on central `develop`.
- [x] Add unit/instrumentation/security coverage and Phase 6 update/session-continuity handoff documentation.
- [x] Render and personally inspect real light/dark/150%-font Settings/update candidates; canonicalize only the accepted replacements.
- [x] Final canonical screenshot regression, representative S24 instrumentation and full normal Android verification green on the final implementation state.
- [x] Merge to Android `develop` with zero unresolved hosted review blockers.
- [x] Keep production signing/release/version freeze and `develop -> main` deferred to the physical Phase 6/signing handoff.

## Phase 6 — physical-device / production / signing handoff

Tracker: issue #14. Checklist: `docs/PHASE_6_DEVICE_HANDOFF.md`. The owner's physical Samsung Galaxy S24 Ultra is authoritative. Use the current `develop` state after the merged corrections and private self-updater passes.

- [ ] Record Samsung One UI / Android version plus the owner's current display resolution, screen zoom and font settings for the accepted run.
- [ ] Re-run production-configured Auth/API on the physical S24 Ultra: login → TOTP/AAL2 → canonical sync.
- [ ] Validate biometric/PIN local unlock after background/kill/relaunch.
- [ ] With networking unavailable, validate local PIN/biometric unlock enters cached finance mode without requiring email/password/TOTP solely because validation is unavailable.
- [ ] While offline, create at least two transactions, navigate normally, kill/relaunch the app and confirm both pending transactions survive and remain visible in Activity.
- [ ] Cancel one never-sent pending transaction locally and confirm it disappears without a server write.
- [ ] Restore networking and verify fresh server reload occurs before replay, the remaining stable event ID is committed once, and no duplicate transaction is created.
- [ ] Exercise the explicit review/retry path for an ambiguous write without any blind automatic resend.
- [ ] Delete one synchronized transaction from Activity detail with confirmation, reload and verify canonical balances/history remain consistent.
- [ ] Perform one additional reversible finance mutation, reload and verify the persisted result without 428/409 corruption or duplication.
- [ ] Validate real card deletion end-to-end against production state and the secret-vault boundaries.
- [ ] Validate owner+AAL2 PAN/expiry access and device-local CVV behavior.
- [ ] Validate Appearance System/Light/Dark persistence on the physical Samsung device.
- [ ] Validate the real Settings Updates section against the production-configured owner+AAL2 update endpoint, including the no-release/up-to-date state.
- [ ] Before production signing, run an in-place updater smoke with two controlled non-production builds signed by the same temporary/test identity.
- [ ] Validate Samsung install-source permission handoff and PackageInstaller system-confirmation fallback without bypassing Android prompts.
- [ ] After that package replacement, verify the encrypted session/PIN/CVV stores survive, local PIN/biometric unlock works and email/password/TOTP is not requested unless the server session is genuinely invalid/expired/revoked.
- [ ] Validate corrected Home, Quick Entry, Settings, Money, Plan and Insights rendering/hierarchy on the owner's unchanged Samsung display/font settings.
- [ ] Capture only current real application screenshots for device-specific acceptance and replace superseded physical evidence.
- [ ] Validate logout → relaunch → re-auth flow.
- [ ] Validate device-specific cold start, scrolling, Quick Entry, reconnect and update-flow performance.
- [ ] After physical acceptance and explicit signing authorization, create/preserve one long-lived production signing identity outside the public repository.
- [ ] Repeat the in-place update smoke with strictly increasing builds signed by that same production identity before release promotion.
- [ ] Promote/freeze a release candidate only after all physical-device and signed update-continuity checks pass.
- [ ] Generate/publish production-signed APKs only after the explicit signing handoff and through the private release path; never as public GitHub artifacts/releases.

Do not create a production signing key, production-signed APK, release or `develop -> main` promotion before the explicit Phase 6 signing handoff and required physical acceptance.
