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

## Read-only signed release comparison — 2026-11-16

Following the completed PR #464 safety UI, the next verification is a **new, read-only GitHub Actions audit** of the already published signed `v1.0.0-rc16` and `v1.0.0-rc17` direct APK assets, using their public checksum/metadata sidecars. The GitHub connector can verify release metadata but cannot return the binary APK bytes; therefore the initial repository-only checks could not inspect APK bytes; the hosted audit later completed successfully, but **no Play Protect clearance occurred**. The isolated audit downloaded both actual files on an ephemeral GitHub runner, checked raw SHA-256/size against both attached sidecars, ran Android `aapt` and `apksigner`, inspected APK ZIP entries and printed a public metadata-only comparison. It must not install, execute, publish, upload, or retain either APK as an artifact, nor access release credentials.

A successful read-only static audit can confirm publication integrity, signer continuity and declared APK permission differences; it cannot determine whether the code is malware-free or why Google displayed **Harmful app blocked**. A Google Play Protect classification appeal and owner device acceptance remain separate gates. Google's official developer guidance: https://developers.google.com/android/play-protect/warning-dev-guidance.

### Verified inspection of the *actual published* rc16 and rc17 assets (2026-11-16)

The read-only [Published APK Security Comparison workflow #43848512116](https://github.com/MariosGiannakaras/MyFinHub-Android-App/actions/runs/43848512116) **succeeded** using GitHub-hosted Android `aapt` and `apksigner` on the actual public APK bytes downloaded from both release tags. The job did **not** install or execute the APKs; it deleted the downloaded binaries from the ephemeral runner and published no APK artifact.

| Item | rc16 | rc17 |
| --- | --- | --- |
| Version code | 10015 | 10016 |
| APK size | 3,962,972 bytes | 3,962,972 bytes |
| SHA-256 | `199a263c6dadc67ec2c40b14671a6543b6a13e15d53425b14992d2e4fc2c2556` | `b17d0963ad50aa761c52d130dd7aa61a89b86a940b7dfa223f2d5433ce51f117` |
| App package | `app.myfinhub.android` | `app.myfinhub.android` |
| ZIP entries / DEX entries | 466 / 2 | 466 / 2 |
| Signing certificate SHA-256 | Same enrolled production signer | Same enrolled production signer |
| Declared permissions | Same | Same |

Both APKs passed release SHA256SUMS and release-metadata matching, standard ZIP header/CRC checks and `apksigner verify`. The audit found **zero permission additions or removals** across the two releases. Both historic APKs still requested `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, which was removed in newer *source* after rc17; that change is not reflected retroactively in the published APK.

**Interpretation:** This narrows the observed difference; it does *not* prove the app harmless, scan all DEX/SDK behavior, identify the Play Protect detection category, or show that Google withdrew its block. Classification review and physical unblocked in-place installation remain separate pending steps. The source of truth for exact audit observations is the linked run, not an inference from a passed CI status alone.
