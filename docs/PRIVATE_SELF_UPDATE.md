# MyFinHub Android — private self-update contract

## Purpose

MyFinHub is privately distributed to the owner's supported Samsung Galaxy S24 Ultra and does not depend on Google Play for updates. The in-app updater is therefore an authenticated private distribution channel, not a public APK feed.

The updater is auxiliary to authentication and finance synchronization. A failed update check, download or installation must never log the user out, clear the encrypted server session, reset the local PIN, or alter the shared owner+AAL2 server-side card vault.

## Trust and distribution boundary

Signed Android APK/AAB distribution is restricted to the explicitly owner-authorized protected production publisher. The current approved process creates an immutable public GitHub prerelease containing only the approved signed binaries, checksums and safe metadata, alongside an authenticated private Supabase update channel. Signing keys, credentials, finance/customer data and personal secrets must never be attached to public releases or ordinary workflow artifacts.

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
- PAN/expiry/CVV remain under the shared owner+AAL2 encrypted server card vault contract, with legacy device-local stores used only for migration when applicable;
- email/password/TOTP are requested only when the existing server session is actually expired, revoked, invalid, or otherwise requires re-authentication.

The physical Phase 6 upgrade smoke is authoritative for this continuity guarantee because hosted CI cannot reproduce the owner's production signing identity and real installed-app upgrade state.

## Publishing boundary — current protected channel

Production signing is already enrolled and must not be re-created or rotated. The only authorized publication mechanism is `.github/workflows/production-private-release.yml` using an exact-head validated open Android-owned release-source PR targeting `develop`, the guarded production request, and the protected existing signing/publisher credentials. Its versionCode must monotonically increase.

The trusted publisher signs and verifies the direct APK with the enrolled production signer, checks package and version identity, uploads bytes to the authenticated private Supabase bucket, re-reads the exact content and publishes matching metadata last. It then creates a new immutable GitHub prerelease with the same signed APK, the signed Play-compatible AAB, checksums and safe metadata. A public source repository is never a location for private signing material, access tokens or personal data.

Latest published candidate: `1.0.0-rc12` / `10011` (2026-10-14); the owner reported rc12 installed on the physical Galaxy S24 Ultra that day. Existing rc11 / 10010 remains the last version with explicit recorded product acceptance. Whether the new installation was an in-place update and whether session/card-data continuity and product smoke checks passed must still be confirmed separately; publication and installation alone cannot establish these facts. See `docs/PRODUCTION_SIGNING_RELEASE.md`.
