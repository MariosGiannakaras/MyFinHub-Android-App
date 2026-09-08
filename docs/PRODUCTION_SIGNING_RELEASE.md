# MyFinHub Android — production signing and release

Current tracker: issue #73. Historical Phase 6 signer enrollment and same-signer continuity are recorded in closed issue #14.

## Permanent signing identity

Production signing is already enrolled. Every direct production APK for `app.myfinhub.android` must be signed by the same long-lived production identity. A different signer is a release blocker and must never be accepted as a normal update.

Do not create, rotate, replace, expose or commit the production signing key during routine releases. Keep an independent secure offline backup; GitHub Environment secrets are not a backup strategy.

## Protected environment

The trusted production workflow is `.github/workflows/production-private-release.yml` and uses GitHub Environment:

`android-production-release`

The protected Environment contains the existing publisher/signing credentials:

- `SUPABASE_RELEASE_PUBLISH_KEY`;
- `ANDROID_PRODUCTION_KEYSTORE_B64`;
- `ANDROID_PRODUCTION_KEYSTORE_PASSWORD`;
- `ANDROID_PRODUCTION_KEY_ALIAS`;
- `ANDROID_PRODUCTION_KEY_PASSWORD`;
- `ANDROID_PRODUCTION_SIGNER_SHA256`.

Private signing material must never be committed, pasted into Issues/PRs/chat, written to Gradle properties, embedded in the APK/AAB, uploaded as a release asset/artifact, or printed in workflow logs. The public signer certificate fingerprint is safe identity metadata.

## Release request and immutable source gate

Normal production publishing is triggered from trusted `develop` by changing exactly:

`.github/release-requests/production.json`

Example:

```json
{
  "source_pr": 123,
  "version_name": "1.0.0-rc7",
  "request_id": "prod-rc7-final-corrections-v1"
}
```

The source must be an open same-repository `android/...` PR targeting `develop`. The latest exact-head `verify`, `screenshot-regression`, and `s24-ultra-target-instrumented` checks must all be successful. The request commit itself may change no other file.

Production version codes use the reserved range beginning at `10000`. The publisher plans the next strictly increasing code from production metadata rather than trusting a hand-entered versionCode.

## What the protected workflow produces

The workflow:

1. validates the owner-authored guarded request;
2. resolves the immutable validated source PR head;
3. plans the next production versionCode;
4. tests the exact source with `ANDROID_UPDATE_CHANNEL=production`;
5. patches versionCode/versionName only in the ephemeral CI workspace;
6. builds an unsigned optimized direct `release` APK;
7. builds an unsigned `playRelease` Android App Bundle;
8. verifies the Play merged manifest does not contain `REQUEST_INSTALL_PACKAGES` or `UPDATE_PACKAGES_WITHOUT_USER_ACTION`;
9. transfers only unsigned build inputs into the protected job;
10. materializes the enrolled keystore only in runner temporary storage;
11. verifies the keystore certificate SHA-256 against the pinned production signer;
12. signs/verifies the direct APK and signs/verifies the Play AAB;
13. create-only uploads the direct APK to the private Supabase update channel and re-reads the exact bytes before publishing metadata;
14. verifies local/public checksums agree with the privately published direct APK;
15. creates an immutable GitHub Release tag pointing at the exact validated source, attaching the signed APK, signed Play AAB, `SHA256SUMS.txt`, and safe `release-metadata.json`;
16. marks version names containing a prerelease suffix such as `-rc7` as GitHub prereleases;
17. removes temporary signing material from the runner.

The existing private update path remains:

`production/<versionCode>/MyFinHub-<versionCode>.apk`

The signed GitHub Release assets are explicitly owner-authorized release outputs. APK/AAB binaries still must not be committed to Git history.

## Direct APK versus Google Play AAB

The direct `release` APK preserves the already-proven private self-update flow and is the artifact used for in-place continuity from the currently installed production-signed candidate.

The `playRelease` AAB is intentionally different only in distribution/update policy:

- package ID remains `app.myfinhub.android`;
- release optimization remains enabled;
- direct/self-update UI is disabled;
- package-installer permissions are removed;
- updates are owned by Google Play.

See `docs/GOOGLE_PLAY_PRIVATE_DISTRIBUTION.md` for the Play Internal testing decision and one-time Play App Signing enrollment requirement.

## GitHub Release policy

The repository may remain public during the explicitly authorized release. The protected workflow may publish the approved production-signed APK/AAB/checksum/metadata files to a GitHub Release. It must never publish the keystore, passwords, publisher credential, signing private key, finance data or authentication material.

A release tag is immutable release identity. Do not overwrite/reuse an existing version tag or replace assets under a previously published version. If a candidate is rejected, publish a strictly higher version instead.

## Physical acceptance and stable promotion

The current installed technical baseline is `1.0.0-rc6` / versionCode `10005`, which was physically rejected for product/UI reasons. The merged correction source is newer than rc6.

The next protected candidate must therefore have a strictly higher versionCode and use the same enrolled signer so Android can update rc6 in place. After publication, perform the authoritative Samsung Galaxy S24 Ultra acceptance described in `docs/PHASE_6_DEVICE_HANDOFF.md`.

Do not claim stable-final product acceptance merely because signing, GitHub Release, private publication, CI or emulator/device-lab checks are green. If the higher candidate has not yet been accepted on the owner's physical S24 Ultra, keep it as a release candidate/prerelease and record that blocker explicitly.

Promotion from `develop` to release-only `main` is deliberate and should represent the exact accepted release state. Do not use `main` as an integration branch.
