# MyFinHub Android — private self-update contract

## Purpose

MyFinHub is privately distributed to the owner's supported Samsung Galaxy S24 Ultra and does not depend on Google Play for updates. The in-app updater is therefore an authenticated private distribution channel, not a public APK feed.

The updater is auxiliary to authentication and finance synchronization. A failed update check, download or installation must never log the user out, clear the encrypted server session, reset the local PIN, or delete the device-local encrypted CVV vault.

## Trust and distribution boundary

Production-signed APKs must never be published as public GitHub releases or public workflow artifacts.

The production update channel uses:

1. the normal MyFinHub owner session at AAL2;
2. `GET /api/android-update` on the MyFinHub backend for release metadata;
3. the private Supabase Storage bucket `android-releases` for the APK;
4. owner+AAL2 RLS for metadata and object download;
5. the public Supabase publishable key plus the current owner bearer token from the existing authenticated session.

The Android client contains no service-role key, storage admin credential, signing key, keystore password, or other release secret. The private object URL is not sufficient by itself to download an APK: Storage still requires the authenticated owner bearer token. Access therefore expires/revokes with the normal server session rather than introducing a second long-lived download credential.

## Release metadata contract

A published release exposes only the data required to verify and install it:

- `versionCode` — positive Android version code;
- `versionName` — human-readable version;
- `downloadUrl` — HTTPS private Storage object URL under the configured `android-releases` bucket;
- `sha256` — 64-character SHA-256 digest of the exact APK bytes;
- `sizeBytes` — exact APK size, bounded to 300 MiB;
- `mandatory` — product policy flag for the UI;
- `notes` — release notes;
- `publishedAt` — ISO-8601 publication timestamp.

The server validates stored metadata before returning it. The Android client validates it again and fails closed on malformed data.

## Android download verification

Before any install session is created, MyFinHub requires all of the following:

1. the current app session is present and AAL2;
2. the metadata endpoint and APK URL use the configured production endpoints;
3. the APK URL is HTTPS and belongs to the configured Supabase host/private bucket path;
4. the announced size is within the supported bound;
5. the download is streamed to app-private cache storage and never buffered as one unbounded payload;
6. the actual byte count exactly matches `sizeBytes`;
7. the calculated SHA-256 exactly matches `sha256`;
8. the archive package name equals the installed MyFinHub package;
9. the archive version code equals the release metadata and is strictly newer than the installed version;
10. the archive signer certificate digest set equals the signer of the currently installed MyFinHub app.

A failed size, digest, package, version or signer check deletes the candidate file and does not start PackageInstaller.

## Installation boundary

MyFinHub uses Android `PackageInstaller` with a full-install session. It does not attempt to bypass platform security.

For privately distributed builds Android may require the user to allow "Install unknown apps" for MyFinHub. Settings opens the OS screen scoped to the MyFinHub package. When the user returns, the app rechecks the permission and can continue the already verified installation.

On Android versions that support no-user-action update requests, MyFinHub requests that mode for a same-package update. Android remains authoritative: if the platform requires confirmation, `STATUS_PENDING_USER_ACTION` is handled by launching the system-provided confirmation intent. A denial or installer failure leaves the currently installed app intact and is presented as a recoverable update failure.

## Session continuity

Updating the package must preserve normal application data. The updater has no logout or credential-clearing API.

After Android replaces the APK and the app process restarts:

- the encrypted stored server session remains subject to the existing session validation/refresh contract;
- the existing local PIN enrollment remains available;
- biometric/PIN local unlock proceeds normally;
- the device-local encrypted CVV vault remains governed by its existing keystore/storage behavior;
- email/password/TOTP are requested only when the existing server session is actually expired, revoked, invalid, or otherwise requires re-authentication.

The physical Phase 6 upgrade smoke is authoritative for this continuity guarantee because hosted CI cannot reproduce the owner's production signing identity and real installed-app upgrade state.

## Publishing boundary

Repository implementation can prepare and test the channel before production signing exists, but it must keep the production release table/bucket empty.

Only after the physical Galaxy S24 Ultra Phase 6 acceptance passes and the owner explicitly starts the signing handoff may the release process:

1. freeze the final `versionCode`/`versionName`;
2. create/use the long-lived production signing identity outside the public repository;
3. build the exact accepted production APK;
4. calculate its exact size and SHA-256;
5. upload the APK privately;
6. publish matching release metadata;
7. perform an update-over-existing-install smoke on the physical S24 Ultra.

Do not publish metadata before the corresponding private APK is fully uploaded and verified. Do not replace a published version's APK bytes in place; publish a strictly newer version instead.
