# Android Developer Console Limited distribution

## Selected distribution path

MyFinHub uses **Android Developer Console Limited distribution** for Google-supported non-public distribution outside Google Play.

This plan is free and is intended for personal/small trusted-group distribution. It supports distribution to up to 20 explicitly authorized devices. The app may still be delivered through channels chosen by the developer, including the existing private MyFinHub updater and GitHub Releases; Android Developer Console provides the package/signing/developer/device authorization layer rather than hosting the APK as a Play listing.

Official references:

- https://developer.android.com/developer-verification/guides/limited-distribution
- https://developer.android.com/developer-verification/guides
- https://support.google.com/android-developer-console/answer/16640817
- https://support.google.com/android-developer-console/answer/16640821

## Current MyFinHub registration

Owner-reported current state:

- package name `app.myfinhub.android` is registered/authorized;
- the existing enrolled production signing certificate is the registered app-signing identity;
- the Samsung Galaxy S24 Ultra used for authoritative acceptance is an authorized device.

The production signing identity must remain unchanged. Do not create or rotate a key to satisfy distribution setup.

## Release artifact

The required Limited-distribution artifact is the direct optimized production APK signed with the existing enrolled MyFinHub production signer.

The protected release workflow publishes the same direct APK through two approved channels:

1. the existing private MyFinHub production updater; and
2. an immutable GitHub Release/prerelease with the signed APK, `SHA256SUMS.txt`, safe release metadata and release notes.

The APK is not committed to Git history. Signing keys/passwords/tokens are never release assets.

A Google Play AAB is **not required** for Android Developer Console Limited distribution. If a Play-compatible AAB is retained as an optional future artifact, it is non-gating and does not change the selected distribution mechanism.

## Device onboarding

For each additional trusted device, use the Android Developer Console Limited-distribution authorization flow. The device owner must explicitly authorize the device through Google's supported QR/link consent flow before relying on Limited distribution protections.

Do not exceed the account's device limit. If MyFinHub later needs distribution beyond the Limited plan, make a separate explicit distribution decision rather than silently switching release channels.

## Release acceptance

For the current release, the authoritative acceptance target remains the already-authorized Samsung Galaxy S24 Ultra. Install the next strictly higher same-signer candidate in place over the current production baseline and verify the full physical acceptance sequence in `docs/PHASE_6_DEVICE_HANDOFF.md`.

Limited-distribution registration does not replace product/runtime acceptance: signer mismatch, forced uninstall, lost app data/session state, or a physically rejected build remain release blockers.
