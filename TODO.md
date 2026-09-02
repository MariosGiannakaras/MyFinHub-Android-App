# MyFinHub Android TODO

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
- [x] Transient/offline local-unlock validation keeps the recoverable session securely locked.
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

## Full transaction entry parity — implementation complete

Tracker: issue #42. Integration state: PR #43 until merged; do not infer PR state from this file.

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
- [x] Keep the S24 Ultra as the sole supported target and leave physical Samsung-specific acceptance to Phase 6.
- [x] Keep release/signing and the explicitly excluded additional privacy/security audit out of this workstream.

## Phase 6 — physical-device / production / signing handoff

Tracker: issue #14. Checklist: `docs/PHASE_6_DEVICE_HANDOFF.md`. The owner's physical Samsung Galaxy S24 Ultra is authoritative.

- [ ] Validate production-configured Auth/API on the physical S24 Ultra.
- [ ] Perform first physical-device run and auth → local unlock → canonical sync → mutation/reconnect → logout/re-auth smoke flow.
- [ ] Validate actual Samsung One UI rendering plus the owner's display resolution/zoom/font settings.
- [ ] Validate device-specific startup/performance behavior.
- [ ] Promote/freeze a release candidate only after physical-device acceptance.
- [ ] Create/preserve a production signing key only at the explicit signing handoff, outside the public repository.
- [ ] Generate a production-signed APK only when explicitly requested after Phase 6 gates pass.

Do not create a release, production signing key or production-signed APK before the explicit Phase 6 signing handoff.
