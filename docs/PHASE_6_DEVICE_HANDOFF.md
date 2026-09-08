# MyFinHub Android — Final physical Galaxy S24 Ultra / signing handoff

## Purpose and current boundary

This document is the executable handoff for the **separate final physical-device/signing stage** after autonomous Android implementation has been merged and validated.

The **owner's physical Samsung Galaxy S24 Ultra is the sole supported device and the authoritative physical acceptance environment**. Hosted CI, screenshot regression and representative S24-target instrumentation are required gates, but they do not replace owner acceptance on the real phone.

Current post-rc6 state:

- `1.0.0-rc6` / versionCode `10005` is a valid same-signer technical publication but was physically rejected by the owner.
- The owner-rejected rc6 correction pass is merged to `develop` and has already passed fresh real Compose light/dark/150%-font inspection, Project Tracking, screenshot regression, representative S24-target instrumentation and full Android CI/R8.
- The merged fixes include account role/name separated from institution, approved provider mapping/branding, canonical card creation/detail and secure secret handling, linked credit-card purchases/payments, stable card-stack selection controls, the approved delete transition and the redesigned `Εικόνα` hierarchy.
- No higher production candidate has been created or published from the corrected `develop` state.
- The production signing identity already exists and is enrolled. **Do not create, replace, rotate or expose a production signing key.**

### What is allowed before explicit signing/publication authorization

Repository and handoff preparation may be completed, but do **not**:

- create a production-signed APK;
- publish a higher production candidate;
- create/replace the production signer;
- perform a release or promote `develop` to `main`.

Because the installed rc6 package is production-signed, a trustworthy in-place acceptance of the merged corrections requires a strictly higher package signed by the **same enrolled production identity**. A debug/test-signed build cannot replace rc6 in place and therefore cannot prove session/PIN/biometric/CVV continuity. Uninstalling rc6 or installing a parallel package is not valid acceptance evidence.

## Repository / workstation prerequisites

Before any physical run:

1. Use the current `develop` integration state with no unresolved supported-device blocker.
2. Confirm `tracking/android-project-state.json`, `STATUS.md`, `TODO.md` and `docs/CURRENT_HANDOFF.md` agree with live GitHub state.
3. Confirm no open implementation PR supersedes the accepted correction baseline.
4. Use JDK 17 and the repository Gradle wrapper.
5. Confirm the normal non-device verification path is green, including:
   - benchmark/Baseline Profile tooling;
   - unit tests and instrumentation compile;
   - lint/debug assembly;
   - optimized unsigned release/R8 analysis;
   - release-manifest and unsigned-APK policy audit;
   - Project Tracking;
   - screenshot regression;
   - representative S24-target instrumentation.
6. Do not place service-role keys, vault keys, signing passwords, keystores, real finance exports or other server/private secrets in the repository or APK.

## Public build-time configuration

The Android app has no end-user infrastructure configuration fields. Its production public-client defaults are compiled through `BuildConfig` and are already present in the public repository.

Public client endpoints/credentials are not service-role or server secrets. A workstation may override documented public Gradle properties only for a deliberate environment change. Never place service-role keys, vault keys or signing material in those properties.

## Physical-device setup

On the owner's Galaxy S24 Ultra:

- Record Samsung One UI / Android version.
- Record the owner's current display resolution, screen zoom and font size settings; do not change them merely to make the UI pass.
- Preserve the currently installed production app and application data when validating an in-place candidate.
- Do not uninstall rc6 as a shortcut around signer/update problems.
- Do not install a parallel package and treat it as continuity evidence.
- Keep the S24 Ultra as the only authoritative physical acceptance target.

## Corrected rc6 product acceptance checklist

After a higher same-signer candidate is explicitly authorized and installed **in place** over the rejected rc6 baseline, verify the owner feedback fixes first.

### Home / account identity

- Primary accounts show semantic account role/name separately from institution/provider.
- Example structure is equivalent to `Μισθοδοσίας` as the account identity and `Τράπεζα Πειραιώς` as the institution, not a combined shorthand label.
- Account/provider identity remains readable at the owner's normal and large-font settings.
- Approved provider mapping/brand marks render correctly where the product owns a supported local provider asset.

### Cards

- Canonical card creation is obvious, reachable and completes through the supported finance mutation boundary.
- Card detail shows the correct account/card/provider identity.
- Credit-card detail exposes linked canonical `card_purchase` / `card_payment` history where present in the synchronized data.
- Card-stack indicators reflect the active card and are usable as stable navigation/position controls.
- Card deletion uses the approved transition quality and still preserves canonical mutation/reconciliation safety.
- PAN/expiry remain behind the server vault boundary and owner+AAL2 checks; CVV remains device-local and protected.

### `Εικόνα`

- The redesigned comparison/trend/category hierarchy is visually clear and materially more useful than the rejected KPI-card-heavy rc6 presentation.
- No clipping, overlap, unreadable labels or broken chart/category hierarchy appears in light, dark or the owner's large-font settings.
- Drill-in/navigation from `Εικόνα` does not leave stale nested destination state.

## Production Auth/API and continuity smoke

1. Launch the in-place updated app and verify the expected version is installed over the existing package.
2. Verify the encrypted stored server session was not cleared by the update.
3. Verify the existing local PIN/biometric enrollment is still present where applicable.
4. After local unlock, verify the stored server session is validated/refreshed normally before product access.
5. Email/password/TOTP should be required only if the server session is genuinely expired, revoked or invalid.
6. Confirm canonical finance data loads and visible balances/activity are plausible.
7. Complete owner+AAL2 card-secret access and verify PAN/expiry/CVV boundaries remain intact.
8. Perform one reversible finance mutation and verify server synchronization completes exactly once.
9. Exercise offline -> online recovery:
   - disconnect before a mutation;
   - verify the UI represents the write as waiting for network and does not pretend it was sent;
   - restore networking;
   - verify fresh server state is loaded before replay/reconcile;
   - verify no duplicate finance event is created.
10. Exercise a transient load/reconnect failure and verify recovery without a stuck loading loop.
11. Confirm logout clears protected local state only when the user actually logs out; updater or network failures must not log the user out.

## Navigation / rendering / accessibility acceptance

Inspect the real app on the owner's unchanged S24 Ultra settings:

- Home.
- Activity / Κινήσεις.
- Money / Περιουσία and supported nested account/card/savings/debt states.
- Plan / Πλάνο.
- `Εικόνα`.
- Quick Entry.
- Settings, Diagnostics and Updates.
- Login, TOTP, PIN enrollment and local unlock states.
- Loading, empty, offline, retry, revision-conflict and pending-network states.
- Card stack/detail and card-secret dialogs.
- Global Snackbar and supported detail dialogs.

Specifically verify:

- top-level reselect returns the destination to root;
- cross-destination navigation does not expose stale nested detail;
- Activity filters and other controls remain usable at large font scale;
- primary actions are not obscured by bottom navigation, keyboard, Snackbar or system insets;
- interactive controls remain comfortably tappable;
- TalkBack labels/state are useful for stateful controls;
- validation errors are understandable without color alone;
- dark mode keeps muted text, borders, progress/status content and disabled controls readable;
- large/negative monetary amounts and long institution/account labels remain readable.

Reject clipping, overlap, inaccessible controls, duplicate rapid navigation, stale detail state, broken large-font behavior or a result that is still visually inadequate despite green hosted checks.

Only current real-application screenshots from the physical S24 Ultra count as device-specific acceptance evidence. Superseded physical screenshots should be replaced rather than retained as current evidence.

## Device-specific performance acceptance

Hosted Macrobenchmark/Baseline Profile results are diagnostic; the physical S24 Ultra is authoritative for final product feel.

Validate at minimum:

- cold start reaches the expected auth/product state without abnormal delay;
- Home and Activity scroll smoothly with realistic data volume;
- Quick Entry opens and submits without visible duplicate/jank behavior;
- Money/Plan/card detail navigation remains responsive;
- switching and reselecting the five top-level destinations remains responsive;
- app relaunch/local unlock has no abnormal delay or loop;
- offline -> online recovery does not freeze navigation or the main thread;
- update checking/downloading remains nonblocking before Android installation takes over.

A reproducible S24 Ultra performance defect is a blocker and must return through the normal `develop` -> short-lived branch -> PR -> hosted validation loop before another candidate is accepted.

## Protected same-signer candidate boundary

Only after explicit owner authorization for the final signing/publication handoff:

1. Use the **existing enrolled long-lived production signing identity**. Do not generate or rotate it.
2. Create a strictly higher version than rc6 from the exact validated corrected `develop` source through the repository's protected private publisher path.
3. Verify package identity, version and enrolled signer before publication.
4. Publish only to the private production update channel; never expose a production APK as a public GitHub artifact/release.
5. Re-read/verify the published bytes and metadata according to the existing protected publisher contract.
6. Install through the app's updater over the existing rc6 package on the owner's S24 Ultra.
7. Treat signer mismatch, forced uninstall, parallel install, lost app data, lost PIN/CVV state or an unexplained full-login requirement as blockers.
8. Do not promote to `main` or declare release completion until owner physical acceptance is explicit.

## Completion record for the current redesign tracker

The original Phase 6 tracker (#14) is historical and already closed. For the current post-Phase-6 redesign/correction work, record authoritative acceptance in **issue #73**.

Record:

- device / One UI / Android version and display/font settings used;
- installed lower baseline and higher tested candidate versions;
- confirmation that the update was in-place with the enrolled production signer;
- session / PIN / biometric / device-local CVV continuity result;
- Home account/institution/provider-branding result;
- card creation/detail/activity/indicator/delete-transition result;
- `Εικόνα` visual/hierarchy result;
- Activity/navigation/large-font/light/dark result;
- production Auth/API, finance mutation and offline/reconcile result;
- updater continuity and failure-isolation result;
- current real-device screenshot evidence;
- device-specific performance result;
- explicit owner acceptance or rejection.

Overall progress must remain unchanged until the authoritative acceptance requirements for the active tracker are actually satisfied. Hosted gates alone cannot close the tracker after a physical owner rejection.
