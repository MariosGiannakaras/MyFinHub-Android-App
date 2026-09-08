# MyFinHub Android — production signing and release

Current tracker: issue #73. Historical Phase 6 signer enrollment and same-signer continuity are recorded in closed issue #14.

## Permanent signing identity

Production signing is already enrolled. Every direct production APK for `app.myfinhub.android` must be signed by the same long-lived production identity. A different signer is a release blocker and must never be accepted as a normal update.

Do not create, rotate, replace, expose or commit the production signing key during routine releases. Keep an independent secure offline backup; GitHub Environment secrets are not a backup strategy.

## Protected environment

The trusted production workflow is `.github/workflows/production-private-release.yml` and uses GitHub Environment `android-production-release`.

The protected Environment contains the existing publisher/signing credentials:

- `SUPABASE_RELEASE_PUBLISH_KEY`;
- `ANDROID_PRODUCTION_KEYSTORE_B64`;
- `ANDROID_PRODUCTION_KEYSTORE_PASSWORD`;
- `ANDROID_PRODUCTION_KEY_ALIAS`;
- `ANDROID_PRODUCTION_KEY_PASSWORD`;
- `ANDROID_PRODUCTION_SIGNER_SHA256`.

Private signing material must never be committed, pasted into Issues/PRs/chat, written to Gradle properties, embedded in the APK/AAB, uploaded as a release asset/artifact, or printed in workflow logs. The public signer certificate fingerprint is safe identity metadata.

## Release request and immutable source gate

Normal production publishing is triggered from trusted `develop` by changing exactly `.github/release-requests/production.json`.

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

## Required release output

The required release artifact is the optimized direct APK signed with the existing enrolled production identity.

The protected workflow must:

1. validate the guarded request and immutable source PR head;
2. plan the next production versionCode;
3. test the exact source with the production update channel;
4. patch versionCode/versionName only in the ephemeral CI workspace;
5. build an unsigned optimized direct APK;
6. transfer only unsigned build input into the protected job;
7. materialize the enrolled keystore only in runner temporary storage;
8. verify its certificate SHA-256 against the pinned production signer;
9. sign and verify package/version/signer identity;
10. create-only upload the direct APK to the private Supabase production update channel;
11. re-read the exact uploaded bytes and publish metadata last;
12. verify the GitHub Release APK checksum matches the privately published bytes;
13. create an immutable GitHub Release/prerelease pointing at the exact validated source, with the signed APK, `SHA256SUMS.txt`, safe `release-metadata.json` and release notes;
14. remove temporary signing material from the runner.

The private update object path remains:

`production/<versionCode>/MyFinHub-<versionCode>.apk`

Signed release binaries must never be committed to Git history.

## Android Developer Console Limited distribution

The selected Google-supported non-public distribution path is **Android Developer Console Limited distribution**.

Owner-reported current setup:

- package `app.myfinhub.android` is registered;
- the enrolled production app-signing certificate is authorized;
- the Samsung Galaxy S24 Ultra acceptance device is authorized.

The direct signed APK remains the installable artifact. It may be delivered through the existing private updater or the GitHub Release while Android Developer Console provides the developer/package/signing/device authorization layer for the Limited plan.

A Google Play Console account, Play testing track, AAB upload or MCP integration is not required for this selected path. See `docs/ANDROID_DEVELOPER_CONSOLE_LIMITED_DISTRIBUTION.md`.

## Optional future Play compatibility

The release-preparation work may retain a Play-compatible build variant/AAB for possible future migration. That artifact is optional and non-gating for Android Developer Console Limited distribution. Its presence must not be treated as a requirement to create a Play account or alter the current release channel.

## GitHub Release policy

The repository may remain public during the explicitly authorized release. The protected workflow may publish approved production-signed APK/checksum/safe-metadata files to a GitHub Release. It must never publish the keystore, passwords, publisher credential, signing private key, finance data or authentication material.

A release tag is immutable release identity. Do not overwrite/reuse an existing version tag or replace assets under a previously published version. If a candidate is rejected, publish a strictly higher version instead.

## Physical acceptance and stable promotion

The current installed technical baseline is `1.0.0-rc6` / versionCode `10005`, which was physically rejected for product/UI reasons. The merged correction source is newer than rc6.

The next protected candidate is `1.0.0-rc7` and must receive a strictly higher versionCode from the publisher. It must use the same enrolled signer so Android can update rc6 in place. After publication, perform the authoritative Samsung Galaxy S24 Ultra acceptance described in `docs/PHASE_6_DEVICE_HANDOFF.md`.

Do not claim stable-final product acceptance merely because signing, GitHub Release, private publication, CI or hosted device checks are green. If rc7 has not yet been accepted on the owner's physical S24 Ultra, keep it as a prerelease and record that blocker explicitly.

Promotion from `develop` to release-only `main` is deliberate and should represent the exact accepted release state. Do not use `main` as an integration branch.
