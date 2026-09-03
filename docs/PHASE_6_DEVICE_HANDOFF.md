# MyFinHub Android — Phase 6 physical Galaxy S24 Ultra handoff

## Purpose

This document starts only after all autonomous Android repository work is merged and green. The **owner's physical Samsung Galaxy S24 Ultra is the sole supported device and the authoritative Phase 6 environment**.

Phase 6 is intentionally different from hosted CI: it verifies the real production public-client configuration, Samsung One UI rendering/display settings, device-specific lifecycle/performance behavior and — only after acceptance — release-candidate/signing work.

## Repository / workstation prerequisites

Before connecting the phone:

1. Use the current `develop` integration state with no unresolved supported-device blocker.
2. Use JDK 17.
3. Use the repository Gradle wrapper (Gradle 9.7.0) and Android Gradle Plugin 9.3.0.
4. Provision Android SDK compileSdk 37; the app targets SDK 36 and minSdk 26.
5. Confirm a clean clone passes the normal non-device verification path. The one-command Gradle validation path is:
   - `./gradlew :benchmark:assembleBenchmark test lint assembleDebug assembleDebugAndroidTest assembleRelease analyzeReleaseR8Config`
   The equivalent split commands are:
   - `./gradlew test lint assembleDebug`
   - `./gradlew :benchmark:assembleBenchmark`
   - `./gradlew assembleDebugAndroidTest assembleRelease analyzeReleaseR8Config`
6. Confirm the normal CI release-manifest and unsigned-APK policy audit is green.
7. Do not place service-role keys, vault keys, signing passwords, keystores, real finance exports or other server/private secrets in the repository or APK.

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
- Enable the normal developer/USB debugging path needed for the first Android Studio/ADB run.
- Install only a non-production-signed development/release-candidate build until the explicit signing handoff.
- Keep the S24 Ultra as the only device acceptance target; do not reopen tablet/foldable work.

## Production Auth/API smoke sequence

Use the real production public-client configuration already intended for the Android app. Do not enter infrastructure secrets into the phone.

1. Cold-launch the app and verify no configuration/error loop appears.
2. Sign in with the owner account.
3. Complete TOTP and verify the session reaches AAL2 before card-secret access.
4. Confirm canonical finance data loads and the visible totals/lists are plausible.
5. Open Money and Plan and verify production state ownership is truthful:
   - account/card/savings/debt/scheduled totals match synchronized data;
   - no fabricated loan name, lender, due date, lending person, savings target, category budget, rule or forecast window appears when the canonical payload does not provide it;
   - unsupported detailed editing is read-only/unavailable rather than presented as a successful synchronized save.
6. From Home, open Quick Entry through the primary add action, choose at least one transaction type and verify the real canonical form opens with that type preselected.
7. Verify the transaction date selector, monetary keyboard/input, field-level validation and first-invalid-field focus/scroll behavior on the physical Samsung keyboard/display settings.
8. Background/kill/relaunch the app and complete biometric/PIN local unlock.
9. Confirm local unlock validates or refreshes the stored server session before product access.
10. Perform one reversible finance mutation and verify server sync completes once.
11. Test the resilience flow deliberately:
   - disconnect networking before a mutation;
   - verify the UI shows the change as waiting for network and no server request is assumed sent;
   - restore networking;
   - verify server state is reloaded before the stable mutation is replayed and no duplicate finance event is created.
12. Test a transient load failure/reconnect and verify the app recovers without a loading loop.
13. Open a card-detail secret flow and verify owner+AAL2 requirements, server PAN/expiry boundary and device-local CVV behavior remain intact.
14. Open Settings and verify account logout is present there rather than as a persistent overlay over finance screens.
15. Log out while the app is otherwise healthy; verify local protected state is cleared.
16. Relaunch and verify login is required, then re-authenticate successfully.

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

The private updater contract is documented in `docs/PRIVATE_SELF_UPDATE.md`. Hosted tests can verify metadata, download integrity, package identity, signer checks and PackageInstaller handoff, but **only an update over an already installed build on the owner's S24 Ultra can validate application-data/session continuity**.

Before production signing is authorized, this smoke may use two non-production builds signed by the same temporary/test identity. Keep the production private release channel unpublished and do not create a production signing key merely to run this test.

1. Install the accepted lower-version build on the physical S24 Ultra.
2. Sign in with the owner account, complete AAL2/TOTP and enroll/verify the normal local PIN/biometric flow.
3. Confirm finance synchronization works and, if appropriate for the smoke, confirm the device-local CVV vault behavior before the update.
4. Publish only the controlled test update metadata/APK to the private test path used for the physical smoke; never expose the APK publicly.
5. Open Settings → Updates and verify the newer version is detected without interrupting normal finance use.
6. Start the in-app download and verify progress is visible and the verified-install state is reached.
7. If Samsung/Android requires "Install unknown apps" permission for MyFinHub, grant it through the OS-scoped permission screen and return to the app. Verify installation resumes correctly.
8. If Android displays the system package-install confirmation, complete it. Do not treat the presence of this platform confirmation as a failure.
9. Let Android replace the installed app, then relaunch MyFinHub.
10. Verify the app reports the newer version and no downgrade/parallel-package installation occurred.
11. Verify the encrypted stored server session was not cleared by the updater. Normal local PIN/biometric unlock should be offered where applicable.
12. After local unlock, verify the existing server session is validated/refreshed normally and finance data loads without an unnecessary email/password/TOTP flow.
13. Email/password/TOTP may be required only if the server session actually expired, was revoked, became invalid or otherwise legitimately requires re-authentication.
14. Verify updater work did not clear the existing local PIN enrollment or the device-local encrypted CVV vault. Re-check card-secret access through the normal owner+AAL2 boundary.
15. Confirm finance mutations/reconnect still behave exactly once after the update and no updater failure can trigger account logout.

At the explicit production signing handoff, repeat the update-over-installed-build smoke with production-signed same-identity builds before private distribution of the first real update. Never change the production signing identity between versions: Android must reject a package signed by a different identity, and MyFinHub independently checks the signer before opening PackageInstaller.

## Samsung rendering / UX acceptance

Inspect the real app on the owner's unchanged S24 Ultra settings:

- Home, including the simplified financial hierarchy and primary Quick Entry action.
- Activity, Money, Plan and Insights.
- Canonical Money nested savings/loan/lending states, especially empty/read-only detail handling.
- Canonical Plan and overall-budget editing.
- Quick Entry, Material date selection, validation/error states and split-entry scrolling.
- Settings/Diagnostics, Settings/Updates and truthful empty Change History.
- Login, TOTP, PIN enrollment, locked/local-unlock states.
- Loading, empty/first-use, offline, retry, revision-conflict and pending-network states.
- Card stack/detail and card-secret dialogs.
- Global Snackbar and details dialog.

Reject clipped text, inaccessible controls, overlapping bottom navigation/FAB/Snackbar, unreadable long labels, broken large/negative amounts, duplicate rapid navigation or stuck loading/recovery states.

Specifically verify the hardening accessibility outcomes on the real device:

- interactive controls remain comfortably tappable at the owner's display/zoom settings;
- Settings switches and other stateful controls announce useful labels/state with TalkBack;
- validation errors are understandable without relying only on color;
- large-font settings do not hide the primary action or make navigation unusable;
- dark mode keeps muted text, borders, progress/status content and disabled controls readable.

Only real application screenshots from this device count as device-specific acceptance evidence.

## Device-specific performance acceptance

Hosted Macrobenchmark/Baseline Profile infrastructure is diagnostic; the physical S24 Ultra is authoritative here.

Validate at minimum:

- cold start feels responsive and reaches the expected auth/product state;
- Home and Activity scrolling remain smooth with realistic data volume;
- Quick Entry opens, date selection/validation responds normally and submit does not visibly jank or duplicate;
- canonical Money/Plan navigation and nested read-only/detail states remain responsive;
- navigation among the five top-level destinations remains responsive;
- app relaunch/local unlock has no abnormal delay or loop;
- offline → online recovery does not block the main thread or freeze navigation;
- Settings update check remains nonblocking and downloading an update does not make normal navigation unusable before installation begins.

If a reproducible S24 Ultra performance defect is found, fix it before release-candidate promotion and repeat the relevant exact-head repository gates.

## Release-candidate / signing boundary

Do **not** do any of the following until all physical-device checks above pass and the product owner explicitly starts the signing handoff:

- freeze final release versionCode/versionName;
- create a production signing key/keystore;
- commit signing material or passwords;
- generate/distribute a production-signed APK;
- create/promote a production release.

At the explicit signing handoff, create/preserve the long-lived production signing key outside the public repository, configure signing through an appropriate local/secure secret mechanism, build the exact accepted release candidate, and repeat the final smoke validation on the physical S24 Ultra before distribution.

## Completion record

When Phase 6 is actually performed, record in issue #14:

- device/One UI/Android version and display/font settings used;
- exact accepted repository state/release version;
- production Auth/API smoke result;
- canonical Money/Plan truthfulness and Quick Entry physical-UX result;
- offline/reconnect and duplicate-write result;
- private update-over-installed-build result, including session/local-unlock continuity;
- Samsung visual/accessibility acceptance result with current real-device screenshots;
- device-specific performance result;
- whether release-candidate promotion/signing was explicitly authorized.

Until those physical checks are performed, Phase 6 remains open even if all repository automation is green.
