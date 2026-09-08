# Phase 6 production signing handoff

## Current authoritative boundary — 2026-09-08

The owner has explicitly authorized the final production signing/release handoff. The earlier stage-specific prohibition on producing a higher production-signed candidate or GitHub Release no longer applies.

The production signing identity is already enrolled and has been used for prior same-signer production candidates. Preserve that identity. Do not create, rotate, replace or expose a signing key.

The currently published `1.0.0-rc6` / versionCode `10005` remains a technically valid same-signer baseline but was physically rejected for product/UI reasons. The correction pass is merged and hosted-validated on `develop`, so the next physical acceptance candidate must be strictly higher than rc6 and signed with the same enrolled identity.

## Authorized sequence

1. Finish and merge PR #92, which makes the direct production APK, checksums, GitHub Release and private update publication reproducible.
2. Create a short-lived exact release-source PR from current `develop`, targeting `develop`, for `1.0.0-rc7` with synchronized canonical tracking.
3. Require exact-head Project Tracking, Android `verify`, screenshot regression and representative S24-target instrumentation to pass.
4. Trigger the protected production publisher. The protected workflow must verify the enrolled signer before signing anything.
5. Produce the direct optimized APK signed with the enrolled production identity. This is the required artifact for same-signer in-place continuity, the private updater, GitHub Release and Android Developer Console Limited distribution.
6. Publish the direct APK to the existing private production update channel, re-read/verify the exact remote bytes, and publish metadata last.
7. Create an immutable GitHub prerelease for the exact validated source with the signed APK, `SHA256SUMS.txt`, safe release metadata and release notes. Do not reuse or overwrite an existing version tag.
8. Install/update the direct candidate in place over the existing rc6 installation on the owner's authorized Samsung Galaxy S24 Ultra.
9. Verify session/PIN/biometric/device-local CVV continuity, production Auth/API, finance mutation/reconcile behavior, updater behavior, corrected Home/cards/Εικόνα, navigation, accessibility/light/dark/large-font and physical-device performance.
10. Treat signer mismatch, forced uninstall, parallel package, lost application data, unexpected full-login requirement, or a physically rejected product result as blockers.
11. Only after explicit physical owner acceptance may a stable-final completion claim and release-only `main` promotion be made.

## Google-supported limited distribution

The selected non-public Google-supported distribution mechanism is **Android Developer Console Limited distribution**, not a public Google Play listing.

Owner-reported setup is complete for the current path:

- `app.myfinhub.android` is registered;
- the enrolled production signing certificate is authorized;
- the Samsung Galaxy S24 Ultra acceptance device is authorized.

The signed APK may continue to be delivered through the existing private updater and GitHub Release. Android Developer Console provides the package/signing/device authorization layer for the Limited plan. A Play Console account, Play track, AAB upload or MCP integration is not required for this selected release path.

See `docs/ANDROID_DEVELOPER_CONSOLE_LIMITED_DISTRIBUTION.md`.

## Optional future Play artifact

If the repository retains a Play-compatible AAB for future use, it is optional and non-gating for the current Limited-distribution release. Do not interpret its presence as a requirement to create a Play Console account or switch distribution mechanisms.

## Security guard

Signing material and publisher credentials remain protected secrets. Authorization to release binaries is not authorization to expose keys/passwords/tokens. Signed APK/checksum artifacts may be published only through the explicitly approved release/distribution channels, never committed to Git history.

## Scope guard

This handoff is Android-only. Central MyFinHub changes are permitted only when strictly required by the Android production release path and must use the Android-owned integration discipline without modifying unrelated web/desktop workstreams.
