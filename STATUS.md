# MyFinHub Android status

## 2026-09-02 — Post-redesign resilience/data-integrity hardening active

The retained native Android product has completed the shared 2026 redesign and first post-review cleanup/reliability pass. The sole supported Android device remains the owner's **Samsung Galaxy S24 Ultra**; `docs/SUPPORTED_DEVICE.md` is the permanent device-acceptance source of truth.

Active follow-up tracker: issue #39. Draft integration PR: #40 into `develop`.

### Completed redesign / baseline hardening

- Shared light/dark Material 3 visual system, authentic branding, compact spacing and centralized curated icon vocabulary.
- Home, Activity, Money, Plan, Insights, Quick Entry, auth and retained secondary/detail/system flows redesigned.
- Issue #24 native credit-card stack and owner+AAL2 secret boundaries preserved.
- Forecast and Backup/Import/Data Transfer user-facing Android scope removed; canonical/desktop-owned finance fields continue to round-trip losslessly.
- Safe `UserNotice` operational-error contract, global Snackbar + safe details, cancellation preservation and recoverable finance/CVV failure handling are merged.
- Final redesign acceptance passed canonical screenshots, representative S24-target instrumentation and normal Android CI/R8/unsigned-release policy gates.

### Active resilience/data-integrity work

Implemented on the active branch so far:

- Shared production HTTP policy with explicit connect/read/write/call timeouts and hidden OkHttp connection retries disabled.
- Bounded explicit retry for safe finance reads only; writes execute once and are never blindly replayed after an ambiguous transport result.
- Android connectivity observation with definite offline/online/unknown states.
- Offline finance load preflight and automatic retry only when the original request is known not to have started.
- Offline finance mutation intent remains visible as pending and is automatically reconciled only when it is known the write was never sent.
- Rapid finance submit guards prevent a second mutation launch before the first coroutine can update UI saving state.
- User/session switches and clear/logout cancel stale finance jobs.
- Canonical integrity validation gates known Android-read collections, stable IDs, dates, money values and revisions before server data becomes product state; unknown desktop-owned fields remain ignored by validation and preserved losslessly.
- Auth login/TOTP offline preflight, synchronous Loading transition and secret-array zeroing prevent credential auto-retry and rapid duplicate auth requests.
- Local unlock stays securely locked through transient/offline validation failures instead of discarding the recoverable stored session.
- Explicit logout always clears the encrypted local session boundary even if remote revoke unexpectedly throws.
- Card-secret reveal/save/delete paths suppress rapid duplicate operations and use the shared timeout/no-hidden-retry HTTP policy.
- Settings now accepts a safe diagnostics snapshot containing only version/build/environment/API host/connectivity/API state/session state/last successful sync/diagnostic code; no credentials, tokens, finance payloads, user IDs, PAN or CVV.
- Nested navigation ignores rapid duplicate pushes of the same route.
- Empty canonical finance data now has explicit projection coverage for first-use states.

### Validation state

The active branch is intentionally still a draft while exact-head compile/tests, diagnostics screenshot evidence and S24-target instrumentation are completed. A prior temporary patch workflow failure was assertion-only and committed no partial diagnostics code; the corrected patch succeeded and self-cleaned.

No tablet/foldable/desktop-like Android work is in scope. The separate additional privacy/security audit proposal is explicitly excluded by product-owner instruction.

## Phase 6 boundary

Issue #14 remains the physical-device/release handoff. This workstream will automate and document everything possible before that checkpoint, but the owner's physical Galaxy S24 Ultra remains authoritative for real production Auth/API validation, Samsung One UI/display/font rendering, device-specific performance and eventual release/signing decisions.

Do not create a release, production signing key or production-signed APK before the explicit Phase 6 signing handoff.
