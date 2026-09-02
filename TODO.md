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
- [x] Final redesign screenshot regression, representative S24-target instrumentation and normal CI/R8/unsigned policy gates passed.

## Post-redesign resilience, data integrity & diagnostics — active

Tracker: issue #39. Draft PR: #40. Target: `develop`.

### Network / offline resilience

- [x] Add explicit production HTTP timeouts.
- [x] Disable hidden OkHttp connection retries so write retry policy remains application-owned.
- [x] Bounded retry for safe finance reads on transient NETWORK/SERVER failures.
- [x] Never automatically retry an attempted finance write after an ambiguous transport result.
- [x] Observe validated Android connectivity state.
- [x] Offline load preflight with reconnect retry only when no request started.
- [x] Preserve an offline finance mutation as pending only when the write is known never to have been sent.
- [x] Reconcile that never-sent mutation after connectivity returns by reloading server state first.
- [x] Guard rapid duplicate finance submissions before coroutine state changes become visible.
- [ ] Add/confirm exact-head instrumentation coverage for offline/reconnect UI recovery.

### Canonical data integrity

- [x] Reject malformed known canonical collection structures before they become product state.
- [x] Detect duplicate stable IDs in known canonical identity collections.
- [x] Validate known dates/months and finite bounded money values.
- [x] Validate revision shape on loaded/saved envelopes.
- [x] Preserve unknown/desktop-owned canonical fields losslessly through mutation.
- [x] Keep empty datasets valid and add first-use projection coverage.
- [x] Confirm write transport failure is single-attempt and revision conflicts preserve mutation intent.
- [ ] Pass full exact-head unit/CI regression suite.

### Auth/session recovery

- [x] Offline login/TOTP preflight without retaining credentials for later automatic retry.
- [x] Synchronous Loading transition prevents rapid duplicate auth requests.
- [x] Transient/offline local-unlock validation leaves the recoverable session securely locked.
- [x] Unauthorized/expired session still clears the stored session and requires login.
- [x] Explicit logout always clears the encrypted local session boundary even if remote revoke throws.
- [x] Finance work is cancelled when auth/user state is cleared or switched.
- [ ] Pass exact-head auth/S24 instrumentation regression.

### S24 Ultra UX edge states

- [x] Empty/first-use canonical data projection covered.
- [x] Rapid duplicate nested-route pushes are suppressed.
- [x] Rapid card-secret reveal/save/delete requests are suppressed.
- [x] Offline/pending finance save has a distinct recoverable UI issue instead of a generic failure.
- [ ] Validate diagnostics/settings and offline/pending states in real 412×915 rendered evidence.
- [ ] Inspect long labels/extreme display values through the existing representative S24-target UI suite and add targeted coverage if a real defect appears.

### Safe in-app diagnostics

- [x] Add safe diagnostics snapshot contract.
- [x] Surface app version/build type, public environment/API host, connectivity, API state, session state, last successful sync and diagnostic code in Settings.
- [x] Keep tokens, credentials, user IDs, PAN/CVV and finance payloads out of diagnostics.
- [x] Capture the latest safe app diagnostic code from the existing notice stream.
- [ ] Add real diagnostics screenshot evidence and personally inspect it.

### Cleanup / Phase 6 preparation

- [ ] Remove remaining unrouted/dead Backup/Import/Data Transfer utilities state/screen/test remnants.
- [ ] Create final physical-S24 Phase 6 handoff checklist and clean-clone prerequisites.
- [ ] Update issue #14 with completed autonomous preparation while leaving real device/signing steps open.
- [ ] Synchronize permanent issue #27, issue #39, PR #40, STATUS/TODO after final validation.
- [ ] Pass exact-head normal CI, screenshot regression and representative S24-target instrumentation.
- [ ] Merge PR #40 into `develop` only after zero unresolved supported-device blocker.

### Explicit exclusion

The additional privacy/security audit proposal (clipboard/recent-app/accessibility-secret audit package) is intentionally **not** implemented in this workstream, per product-owner instruction.

## Phase 6 — physical-device / production / signing handoff

Tracker: issue #14. The owner's physical Samsung Galaxy S24 Ultra is authoritative.

- [ ] Validate production-configured Auth/API on the physical S24 Ultra.
- [ ] Perform first physical-device run and auth → local unlock → canonical sync → mutation/reconnect → logout/re-auth smoke flow.
- [ ] Validate actual Samsung One UI rendering plus the owner's display resolution/zoom/font settings.
- [ ] Validate device-specific startup/performance behavior.
- [ ] Promote/freeze a release candidate only after device acceptance.
- [ ] Create/preserve a production signing key only at the explicit signing handoff, outside the public repository.
- [ ] Generate a production-signed APK only when explicitly requested after Phase 6 gates pass.

Do not create a release, production signing key or production-signed APK before the explicit Phase 6 signing handoff.
