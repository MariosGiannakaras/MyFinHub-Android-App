# MyFinHub Android — Phase 6 physical Galaxy S24 Ultra handoff

## Purpose

This document is the executable handoff after autonomous Android repository work is merged and green. The **owner's physical Samsung Galaxy S24 Ultra is the sole supported device and the authoritative physical acceptance environment**.

Physical acceptance is intentionally different from hosted CI: it verifies the real production public-client configuration, Samsung One UI rendering/display settings, device-specific lifecycle/performance behavior and — only when explicitly authorized — the protected same-signer candidate/signing path.

### Current post-rc6 checkpoint

- `1.0.0-rc6` / versionCode `10005` is a valid production-signed technical baseline but was physically rejected by the owner.
- The owner-rejected rc6 correction pass is merged to `develop` and has already passed fresh real Compose light/dark/150%-font inspection, Project Tracking, screenshot regression, representative S24-target instrumentation and full Android CI/R8.
- The merged corrections cover semantic account role/name plus separate institution/provider identity, approved provider mapping/branding, canonical card creation/detail and secure secret handling, linked credit-card purchases/payments, stable card-stack selection controls, the approved delete transition and the redesigned `Εικόνα` hierarchy.
- No higher production candidate has been created or published from the corrected `develop` state.
- The long-lived production signing identity already exists and is enrolled. **Do not create, replace, rotate or expose a production signing key.**
- Until the owner explicitly authorizes the separate final signing/publication handoff, do not create a production-signed APK, publish a higher production candidate, perform a release or promote `develop` to `main`.

Because the installed rc6 package is production-signed, authoritative continuity acceptance of the merged corrections requires a strictly higher package signed by the **same enrolled production identity**. A debug/test-signed build cannot replace rc6 in place. Uninstalling rc6 or installing a parallel package is not valid evidence for session/PIN/biometric/CVV continuity.

## Repository / workstation prerequisites

Before connecting the phone:

1. Use the current `develop` integration state with no unresolved supported-device blocker.
2. Confirm `tracking/android-project-state.json`, `STATUS.md`, `TODO.md` and `docs/CURRENT_HANDOFF.md` agree with live GitHub state.
3. Confirm no open implementation PR supersedes the accepted correction baseline.
4. Use JDK 17.
5. Use the repository Gradle wrapper (Gradle 9.7.0) and Android Gradle Plugin 9.3.0.
6. Provision Android SDK compileSdk 37; the app targets SDK 36 and minSdk 26.
7. Confirm a clean clone passes the normal non-device verification path. The one-command Gradle validation path is:
   - `./gradlew :benchmark:assembleBenchmark test lint assembleDebug assembleDebugAndroidTest assembleRelease analyzeReleaseR8Config`
   The equivalent split commands are:
   - `./gradlew test lint assembleDebug`
   - `./gradlew :benchmark:assembleBenchmark`
   - `./gradlew assembleDebugAndroidTest assembleRelease analyzeReleaseR8Config`
8. Confirm Project Tracking, screenshot regression, representative S24-target instrumentation, the normal CI release-manifest audit and unsigned-APK policy audit are green for the accepted implementation state.
9. Do not place service-role keys, vault keys, signing passwords, keystores, real finance exports or other server/private secrets in the repository or APK.

## Public build-time configuration

The Android app has no end-user configuration fields. Its production public-client defaults are compiled through `BuildConfig` and are already present in the public repository:

- `MYFINHUB_API_BASE_URL=https://mgfinhub.vercel.app`
- `SUPABASE_URL=https://ahsukppxwaiagampsuzb.supabase.co`
- `SUPABASE_PUBLISHABLE_KEY=sb_publishable_Ee7nzCpHN5AKwjXkPBvxdw_bTJXoJGC`

These values identify public client endpoints/credentials and are **not** service-role or server secrets. A workstation can override any of them with the Gradle properties of the same names when a deliberate environment change is required. Do not add service-role keys, vault keys or signing material to these properties.

## Physical-device setup

On the owner's Galaxy S24 Ultra:

- Record Samsung One UI / Android version.
- Record the owner's current display resolution, screen zoom and font size settings; do not change them merely to make the UI pass.
- Enable the normal developer/USB debugging path if needed for diagnostics, without replacing the installed production package with a differently signed build.
- Preserve the installed rc6 package and its application data until a same-signer in-place candidate is explicitly authorized.
- Do not uninstall rc6 as a shortcut around signer/update problems.
- Do not install a parallel package and treat it as continuity evidence.
- Keep the S24 Ultra as the only device acceptance target; do not reopen tablet/foldable work.

## Corrected rc6 product acceptance

After a strictly higher same-signer candidate is explicitly authorized and installed **in place** over rc6, verify the owner rejection points first.

### Home / account identity and provider branding

- Primary accounts show semantic account role/name separately from institution/provider.
- Example structure is equivalent to `Μισθοδοσίας` as the account identity and `Τράπεζα Πειραιώς` as the institution, not a combined shorthand label.
- Account/provider identity remains readable at the owner's normal and large-font settings.
- Approved provider mapping/brand marks render correctly where the product owns a supported local provider asset.

### Cards

- Canonical card creation is obvious, reachable and completes through the supported finance mutation boundary.
- Card detail shows the correct account/card/provider identity.
- Credit-card detail exposes linked canonical `card_purchase` / `card_payment` history where present in synchronized data.
- Card-stack indicators reflect the active card and work as stable navigation/position controls.
- Card deletion uses the approved transition quality while preserving canonical mutation/reconciliation safety.
- PAN/expiry remain behind the server vault boundary and owner+AAL2 checks; CVV remains device-local and protected.

### `Εικόνα`

- The redesigned comparison/trend/category hierarchy is materially clearer than the rejected rc6 KPI-card-heavy presentation.
- No clipping, overlap, unreadable labels or broken chart/category hierarchy appears in light, dark or the owner's large-font settings.
- Drill-in/navigation does not expose stale nested destination state.

## Production Auth/API smoke sequence

Use the real production public-client configuration already intended for the Android app. Do not enter infrastructure secrets into the phone.

1. Cold-launch the in-place updated app and verify no configuration/error loop appears.
2. Verify the expected higher version is installed over the existing package; no uninstall or parallel package occurred.
3. Verify the encrypted stored server session was not cleared by the update.
4. Verify the existing local PIN/biometric enrollment is still present where applicable.
5. After local unlock, confirm the stored server session is validated/refreshed before product access.
6. Email/password/TOTP should be required only if the server session actually expired, was revoked or otherwise became invalid.
7. Confirm canonical finance data loads and the visible totals/lists are plausible.
8. Open Money and Plan and verify production state ownership is truthful:
   - account/card/savings/debt/scheduled totals match synchronized data;
   - no fabricated loan name, lender, due date, lending person, savings target, category budget, rule or forecast window appears when the canonical payload does not provide it;
   - unsupported detailed editing is read-only/unavailable rather than presented as a successful synchronized save.
9. From Home, open Quick Entry through the primary add action, choose at least one transaction type and verify the real canonical form opens with that type preselected.
10. Verify the transaction date selector, monetary keyboard/input, field-level validation and first-invalid-field focus/scroll behavior on the physical Samsung keyboard/display settings.
11. Perform one reversible finance mutation and verify server sync completes once.
12. Test the resilience flow deliberately:
   - disconnect networking before a mutation;
   - verify the UI shows the change as waiting for network and no server request is assumed sent;
   - restore networking;
   - verify server state is reloaded before the stable mutation is replayed and no duplicate finance event is created.
13. Test a transient load failure/reconnect and verify the app recovers without a loading loop.
14. Open a card-detail secret flow and verify owner+AAL2 requirements, server PAN/expiry boundary and device-local CVV behavior remain intact.
15. Open Settings and verify account logout is present there rather than as a persistent overlay over finance screens.
16. Log out only as a deliberate final auth-flow check; verify local protected state is cleared.
17. Relaunch and verify login is required, then re-authenticate successfully.

## Safe diagnostics check

Open Settings → Diagnostics and verify only safe support metadata is visible:

- app version/build type;
- public environment/API host;
- network state;
- API/sync state;
- session state/AAL level;
- last successful sync timestamp;
- last diagnostic code.

There must be no password, PIN, TOTP, access/refresh token, user identifier, finance payload, account/transaction content, PAN, expiry or CVV in the diagnostics surface.

## Private self-update physical upgrade smoke

The private updater contract is documented in `docs/PRIVATE_SELF_UPDATE.md`. Hosted tests can verify metadata, download integrity, package identity, signer checks and PackageInstaller handoff, but **only an update over the already installed production package on the owner's S24 Ultra can validate application-data/session continuity for the current rc6 correction acceptance**.

A two-build non-production same-test-signer rehearsal remains useful only on a fresh/non-production installation for updater mechanics. It does **not** count as acceptance of the current rc6 -> corrected-candidate continuity because a test signer cannot replace the installed production-signed rc6 package.

For the authoritative current smoke, only after explicit owner authorization for the final signing/publication handoff:

1. Use the existing enrolled production signing identity; do not generate or rotate it.
2. Create a strictly higher candidate from the exact validated corrected `develop` state through the protected private publisher path.
3. Verify package/version/signer identity and published bytes/metadata according to the protected publisher contract.
4. Open Settings → Updates on the installed rc6 build and verify the newer version is detected without interrupting normal finance use.
5. Start the in-app download and verify progress is visible and the verified-install state is reached.
6. If Samsung/Android requires "Install unknown apps" permission for MyFinHub, grant it through the OS-scoped permission screen and return to the app. Verify installation resumes correctly.
7. If Android displays the system package-install confirmation, complete it. Do not treat the presence of this platform confirmation as a failure.
8. Let Android replace the installed app, then relaunch MyFinHub.
9. Verify the app reports the newer version and no downgrade/parallel-package installation occurred.
10. Verify the encrypted stored server session was not cleared by the updater. Normal local PIN/biometric unlock should be offered where applicable.
11. After local unlock, verify the existing server session is validated/refreshed normally and finance data loads without an unnecessary email/password/TOTP flow.
12. Verify updater work did not clear the existing local PIN enrollment or the device-local encrypted CVV vault. Re-check card-secret access through the normal owner+AAL2 boundary.
13. Confirm finance mutations/reconnect still behave exactly once after the update and no updater failure can trigger account logout.

Never change the production signing identity between versions: Android must reject a package signed by a different identity, and MyFinHub independently checks the signer before opening PackageInstaller.

## Samsung rendering / UX acceptance

Inspect the real app on the owner's unchanged S24 Ultra settings:

- Home, including corrected account/institution/provider identity and the primary Quick Entry action.
- Activity / Κινήσεις, including filters and navigation behavior.
- Money / Περιουσία, including card stack/detail, creation and linked credit-card activity.
- Plan / Πλάνο.
- `Εικόνα`, including the redesigned comparison/trend/category hierarchy.
- Canonical Money nested savings/loan/lending states, especially empty/read-only detail handling.
- Canonical Plan and overall-budget editing.
- Quick Entry, Material date selection, validation/error states and split-entry scrolling.
- Settings/Diagnostics, Settings/Updates and truthful empty Change History.
- Login, TOTP, PIN enrollment, locked/local-unlock states.
- Loading, empty/first-use, offline, retry, revision-conflict and pending-network states.
- Card stack/detail and card-secret dialogs.
- Global Snackbar and details dialog.

Reject clipped text, inaccessible controls, overlapping bottom navigation/FAB/Snackbar, unreadable long labels, broken large/negative amounts, duplicate rapid navigation, stale nested detail, broken card indicator behavior or stuck loading/recovery states.

Specifically verify the hardening accessibility/navigation outcomes on the real device:

- interactive controls remain comfortably tappable at the owner's display/zoom settings;
- Settings switches and other stateful controls announce useful labels/state with TalkBack;
- validation errors are understandable without relying only on color;
- large-font settings do not hide the primary action or make Activity filters/navigation unusable;
- dark mode keeps muted text, borders, progress/status content and disabled controls readable;
- top-level destination reselect returns the destination to root;
- cross-destination drill-in does not expose stale nested detail.

Only current real application screenshots from this device count as device-specific acceptance evidence. Replace superseded physical screenshots rather than treating old evidence as current.

## Device-specific performance acceptance

Hosted Macrobenchmark/Baseline Profile infrastructure is diagnostic; the physical S24 Ultra is authoritative here.

Validate at minimum:

- cold start feels responsive and reaches the expected auth/product state;
- Home and Activity scrolling remain smooth with realistic data volume;
- Quick Entry opens, date selection/validation responds normally and submit does not visibly jank or duplicate;
- canonical Money/Plan/card-detail navigation remains responsive;
- navigation among the five top-level destinations remains responsive;
- app relaunch/local unlock has no abnormal delay or loop;
- offline → online recovery does not block the main thread or freeze navigation;
- Settings update check remains nonblocking and downloading an update does not make normal navigation unusable before installation begins.

If a reproducible S24 Ultra performance defect is found, fix it before candidate acceptance and repeat the relevant exact-head repository gates.

## Release-candidate / signing boundary

Do **not** do any of the following until the owner explicitly starts the separate final signing/publication handoff:

- create or replace a production signing key/keystore;
- expose signing material or passwords;
- generate/distribute a production-signed APK;
- publish a higher production candidate;
- create/promote a production release;
- promote `develop` to `main`.

At explicit authorization, use only the **existing enrolled long-lived production signing identity** and the protected private publisher. Build/publish the exact validated corrected candidate, verify package/version/signer and byte integrity, then perform the in-place rc6 -> higher-candidate smoke on the physical S24 Ultra before any release completion claim.

Treat signer mismatch, forced uninstall, parallel install, lost application data, lost PIN/CVV state or an unexplained full-login requirement as blockers.

## Completion record

The original Phase 6 tracker (#14) is historical and already closed. For the current post-Phase-6 redesign/correction work, record authoritative acceptance in **issue #73**.

Record:

- device/One UI/Android version and display/font settings used;
- installed lower baseline and higher tested candidate versions;
- confirmation that the update was in-place with the enrolled production signer;
- session/PIN/biometric/device-local CVV continuity result;
- Home account/institution/provider-branding result;
- card creation/detail/activity/indicator/delete-transition result;
- `Εικόνα` visual/hierarchy result;
- Activity/navigation/large-font/light/dark result;
- production Auth/API smoke result;
- canonical Money/Plan truthfulness and Quick Entry physical-UX result;
- offline/reconnect and duplicate-write result;
- private update-over-installed-build result, including failure isolation;
- Samsung visual/accessibility acceptance result with current real-device screenshots;
- device-specific performance result;
- explicit owner acceptance or rejection.

Overall progress must remain unchanged until the authoritative physical acceptance requirements for issue #73 are actually satisfied. Hosted gates alone cannot close the tracker after a physical owner rejection.
