# Google Play Protect warning — MyFinHub Android rc17

## Confirmed incident

On 2026-11-14, the owner reported that a Samsung Galaxy S24 Ultra running the installed MyFinHub `1.0.0-rc16` / `10015` displayed the Google Play Protect **"Harmful app blocked"** dialog while attempting to install `1.0.0-rc17` / `10016`. The OS did not confirm a successful upgrade. The screenshot does not disclose the detection category or the root cause. **Do not call this a false positive without investigation or Google review.**

- Android package: `app.myfinhub.android`.
- Flagged release: https://github.com/MariosGiannakaras/MyFinHub-Android-App/releases/tag/v1.0.0-rc17
- Released direct APK SHA-256: `b17d0963ad50aa761c52d130dd7aa61a89b86a940b7dfa223f2d5433ce51f117`.
- Release source: PR #460, immutable head `c3dc510c92d13e522790158d3aee4f941f9f8c40`; protected publisher run #43610151028 was successful.
- Signed release validation proves the release matched the enrolled signer and integrity checks; it **does not** prove a clean Play Protect verdict or software security.
- The rc16 and rc17 main manifests requested the same permissions, including `REQUEST_INSTALL_PACKAGES` and `UPDATE_PACKAGES_WITHOUT_USER_ACTION`; do not infer why Google flagged rc17 from this fact alone.

## Completed source-code hardening after rc17

- PR #462 (merged `89149011403f68d8242985ab8a7c796bf469e777`): remove unattended-update privilege and explicitly require user approval for private installs.
- PR #463 (merged `bfced0ec980a10a255a7acd8b31a8d43e4acf2dd`): reject direct release APKs with unnecessary unattended-install privilege before the protected signing step.
- PR #461 (merged `4fb96da1381a26cea45f5c1bba4a1a069a35e8e7`): revalidate cached APK size, SHA-256, package/version and signer before an install session.
- Follow-up native safety UI: distinguish Android `PackageInstaller.STATUS_FAILURE_BLOCKED` from generic failure and withhold a retry action for the rejected APK.

None of these changes retroactively updates the already published rc17 or proves Play Protect reclassification.

## Review and release gates

Google's official [Play Protect developer guidance](https://developers.google.com/android/play-protect/warning-dev-guidance) classifies this exact prompt separately from "unknown app" warnings. Before submitting an appeal, audit the actual signed APK and its ZIP/APK structure, permissions, embedded SDKs and app behavior; preserve evidence without uploading finance data, tokens, signing keys or private user information. The public build's APK identity and digest may be provided.

If the developer review supports an erroneous classification, the repository owner can use the [Google Play Protect classification appeal](https://support.google.com/googleplay/android-developer/contact/protectappeals). An appeal is not submitted by this repository change, and no Google verdict is assumed.

Do not bypass Play Protect, sideload rc17 through "Install anyway", uninstall rc16, clear application data, or rotate the enrolled signer. A later **separately authorized** signed candidate must pass protected release gates and demonstrate an unblocked **physical owner S24** in-place update with Recents privacy, biometric/PIN relock, encrypted finance/cache integrity and credit transaction acceptance. D7/W6.4 owner acceptance remains open; D8 is separate.

## Static published-APK evidence checkpoint — 2026-11-16

PR #465 adds a read-only GitHub Actions inspection of the exact published rc16/rc17 APK bytes and safe release metadata (pinned SHA-256, size, standard ZIP header and CRC, package/version, merged permissions, installed production signer certificate continuity). Only a text report is retained, never the APK itself. GitHub Actions run #43845125747 **succeeded** on the real signed rc16/10015 and rc17/10016 public release APKs: pinned SHA-256, release metadata, valid APK ZIP header and all member CRCs, correct package/version and the same enrolled signing certificate. Both archives have 466 entries, two DEX files, identical entry paths and the same seven merged permissions. Exactly nine members differ by CRC/size: AndroidManifest.xml, META-INF/MANIFEST.MF, META-INF/MYFINHUB.RSA, META-INF/MYFINHUB.SF, META-INF/version-control-info.textproto, assets/dexopt/baseline.prof, assets/dexopt/baseline.profm, classes.dex, classes2.dex. No unexpected SMS/Accessibility permission was detected. The historical rc17 includes UPDATE_PACKAGES_WITHOUT_USER_ACTION; its removal in merged source has NOT been released. These observations **do not prove malware absence, SDK runtime safety, Play Protect reclassification or successful physical Samsung S24 installation**. The owner reported blocking remains unresolved; the official Google classification appeal is a separate owner action if warranted by verified evidence.
