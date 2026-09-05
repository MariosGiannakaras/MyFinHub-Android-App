# Phase 6 production signing handoff

## Current authoritative boundary — 2026-09-05

The protected Phase 6 private test-release path and the final supported-device correction pass are complete.

- The validated canonical UI/UX correction pass is merged into Android `develop`.
- The protected `phase6-test` publisher is proven end-to-end with a private NON-PROD build, exact post-upload byte verification and metadata-last publication.
- The product owner completed the authoritative Samsung Galaxy S24 Ultra acceptance for updater discovery/download/install, Samsung/Android confirmation, in-place package replacement, session/local-data continuity and the remaining physical UI/interaction delta.
- The UI/interaction tracker and protected test-publisher tracker are closed.
- Issue #14 remains the Phase 6 source of truth.

## Explicit authorization boundary

Do not create or use a production signing identity, do not publish a production-signed APK, do not freeze the final version, and do not promote Android `develop` to `main` until the product owner explicitly authorizes the production signing handoff.

The completed physical NON-PROD acceptance is a prerequisite, not authorization for production signing.

## Sequence after explicit production-signing authorization

1. Create and preserve one long-lived Android production signing identity outside the public repository. Never commit or log the private key or passwords.
2. Build the exact accepted production baseline from the authoritative Android `develop` state and sign it with that identity.
3. Build a strictly higher-version candidate with the same production identity.
4. Publish only through the protected private production release path; never expose the production APK as a public GitHub artifact or release.
5. On the owner's Samsung Galaxy S24 Ultra, verify an in-place update with no uninstall and no parallel package.
6. Verify encrypted session, PIN and device-local CVV continuity. Local biometric/PIN unlock must still work; email/password/TOTP must not be required unless the server session is genuinely invalid, expired or revoked.
7. Treat signer mismatch, forced uninstall, lost application data, unexpected full-login requirement, or updater failure that leaves the installed app unusable as a Phase 6 blocker.
8. Only after the production same-signer update-continuity smoke passes may the final version be frozen and the release candidate be promoted through the repository's `develop -> main` workflow.

## Scope guard

This handoff is Android-only. Central MyFinHub changes are permitted only when strictly required by the Android production release path and must use the Android-owned integration discipline without modifying unrelated web/desktop workstreams.
