# Phase 6 production signing handoff

## Current authoritative boundary — 2026-09-08

The owner has explicitly authorized the final production signing/release handoff. The earlier stage-specific prohibition on producing a higher production-signed candidate or GitHub Release no longer applies.

The production signing identity is not new: it is already enrolled and has been used for the prior same-signer production candidates. Preserve that identity. Do not create, rotate, replace or expose a signing key.

The currently published `1.0.0-rc6` / versionCode `10005` remains a technically valid same-signer baseline but was physically rejected for product/UI reasons. The correction pass is merged and hosted-validated on `develop`, so the next physical acceptance candidate must be strictly higher than rc6 and signed with the same enrolled identity.

## Authorized sequence

1. Finish and merge the release-preparation changes that make the direct APK, Google Play AAB, checksums, GitHub Release and private update publication reproducible.
2. Create a short-lived exact release-source PR from current `develop`, targeting `develop`, with synchronized canonical tracking.
3. Require exact-head Project Tracking, Android `verify`, screenshot regression and representative S24-target instrumentation to pass.
4. Trigger the protected production publisher for the next version. The protected workflow must verify the enrolled signer before signing anything.
5. Produce both:
   - a direct optimized APK signed with the enrolled production identity for same-signer in-place continuity/private updater/GitHub Release; and
   - a Google Play-compatible AAB with direct installer permissions/self-update UI removed.
6. Publish the direct APK to the existing private production update channel, re-read/verify the exact remote bytes, and publish metadata last.
7. Create an immutable GitHub Release for the exact validated source with the signed APK, signed Play AAB, `SHA256SUMS.txt`, safe release metadata and release notes. Do not reuse/overwrite an existing version tag.
8. Install/update the direct candidate in place over the existing rc6 installation on the owner's Samsung Galaxy S24 Ultra.
9. Verify session/PIN/biometric/device-local CVV continuity, production Auth/API, finance mutation/reconcile behavior, updater behavior, corrected Home/cards/Εικόνα, navigation, accessibility/light/dark/large-font and physical-device performance.
10. Treat signer mismatch, forced uninstall, parallel package, lost application data, unexpected full-login requirement, or a physically rejected product result as blockers.
11. Only after explicit physical owner acceptance may a stable-final completion claim and release-only `main` promotion be made.

## Google Play limited distribution

The selected Google-supported non-public distribution method is **Google Play Internal testing** (or Closed testing if a wider private tester set is later required). See `docs/GOOGLE_PLAY_PRIVATE_DISTRIBUTION.md`.

The repository now prepares the compliant AAB, but first-time Play Console app creation/Play App Signing enrollment/tester-track configuration requires actual Play Console access. When enrolling, preserve the existing app-signing identity so Google-delivered updates remain compatible with the installed MyFinHub package.

## Security guard

Signing material and publisher credentials remain protected secrets. Authorization to release binaries is not authorization to expose keys/passwords/tokens. Signed APK/AAB/checksum artifacts may be published only through the explicitly approved release/distribution channels, never committed to Git history.

## Scope guard

This handoff is Android-only. Central MyFinHub changes are permitted only when strictly required by the Android production release path and must use the Android-owned integration discipline without modifying unrelated web/desktop workstreams.
