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
5. Confirm a clean clone passes the normal non-device verification path:
   - `./gradlew test lint assembleDebug`
   - `./gradlew :benchmark:assembleBenchmark`
   - `./gradlew assembleRelease analyzeReleaseR8Config`
6. Confirm the normal CI release-manifest and unsigned-APK policy audit is green.
7. Do not place service-role keys, vault keys, signing passwords, keystores, real finance exports or other server/private secrets in the repository or APK.

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
5. Background/kill/relaunch the app and complete biometric/PIN local unlock.
6. Confirm local unlock validates or refreshes the stored server session before product access.
7. Perform one reversible finance mutation and verify server sync completes once.
8. Test the resilience flow deliberately:
   - disconnect networking before a mutation;
   - verify the UI shows the change as waiting for network and no server request is assumed sent;
   - restore networking;
   - verify server state is reloaded before the stable mutation is replayed and no duplicate finance event is created.
9. Test a transient load failure/reconnect and verify the app recovers without a loading loop.
10. Open a card-detail secret flow and verify owner+AAL2 requirements, server PAN/expiry boundary and device-local CVV behavior remain intact.
11. Log out while the app is otherwise healthy; verify local protected state is cleared.
12. Relaunch and verify login is required, then re-authenticate successfully.

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

## Samsung rendering / UX acceptance

Inspect the real app on the owner's unchanged S24 Ultra settings:

- Home, Activity, Money, Plan and Insights.
- Quick Entry and edit/detail flows.
- Settings/Diagnostics and Change History.
- Login, TOTP, PIN enrollment, locked/local-unlock states.
- Loading, empty/first-use, offline, retry, revision-conflict and pending-network states.
- Card stack/detail and card-secret dialogs.
- Global Snackbar and details dialog.

Reject clipped text, inaccessible controls, overlapping bottom navigation/FAB/Snackbar, unreadable long labels, broken large/negative amounts, duplicate rapid navigation or stuck loading/recovery states.

Only real application screenshots from this device count as device-specific acceptance evidence.

## Device-specific performance acceptance

Hosted Macrobenchmark/Baseline Profile infrastructure is diagnostic; the physical S24 Ultra is authoritative here.

Validate at minimum:

- cold start feels responsive and reaches the expected auth/product state;
- Home and Activity scrolling remain smooth with realistic data volume;
- Quick Entry opens and submits without visible jank or duplicate action;
- navigation among the five top-level destinations remains responsive;
- app relaunch/local unlock has no abnormal delay or loop;
- offline → online recovery does not block the main thread or freeze navigation.

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
- offline/reconnect and duplicate-write result;
- Samsung visual acceptance result with current real-device screenshots;
- device-specific performance result;
- whether release-candidate promotion/signing was explicitly authorized.

Until those physical checks are performed, Phase 6 remains open even if all repository automation is green.
