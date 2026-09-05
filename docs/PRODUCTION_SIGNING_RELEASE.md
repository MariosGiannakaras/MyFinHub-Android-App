# MyFinHub Android — production signing and private release

Tracker: issue #14.

## Permanent signing rule

Production signing is enrolled once. Every production APK for `app.myfinhub.android` must be signed by the same long-lived production identity. A different signer is a release blocker and must never be accepted as a normal update.

The production private updater channel is `production`. Production release objects use:

`production/<versionCode>/MyFinHub-<versionCode>.apk`

Production version codes use the reserved range beginning at `10000`, separate from the `phase6-test` 6000-series test builds.

## Protected environment

The trusted production workflow is `.github/workflows/production-private-release.yml` and uses GitHub Environment:

`android-production-release`

That Environment must be restricted to the trusted Android `develop` release path and must contain these secrets:

- `SUPABASE_RELEASE_PUBLISH_KEY` — dedicated server-side Supabase credential for the private Android publisher;
- `ANDROID_PRODUCTION_KEYSTORE_B64` — base64 of the long-lived production PKCS12/JKS keystore;
- `ANDROID_PRODUCTION_KEYSTORE_PASSWORD`;
- `ANDROID_PRODUCTION_KEY_ALIAS`;
- `ANDROID_PRODUCTION_KEY_PASSWORD`;
- `ANDROID_PRODUCTION_SIGNER_SHA256` — lowercase or colon-separated SHA-256 of the enrolled signer certificate.

The keystore/private key and passwords must never be committed, pasted into chat, Issues or PRs, written to Gradle properties, embedded in the APK, uploaded as a GitHub artifact, or printed in workflow logs. Keep a separate secure offline backup of the production signing identity; GitHub Environment secrets are not a backup strategy.

The signer fingerprint is safe public identity metadata, but the workflow still reads it from the protected Environment so the keystore and expected identity are enrolled together. The publish job verifies the exact certificate digest before signing and verifies the signed APK again before any upload.

## One-time signer enrollment

Create the long-lived production signing identity outside the public repository on a trusted owner-controlled workstation. Use a strong unique keystore password and key password and preserve the keystore plus recovery information in secure offline storage.

Export the signer certificate and calculate its SHA-256. Store the keystore, passwords, alias and expected fingerprint directly in the protected GitHub Environment. Do not pass any of those private values through ChatGPT or repository content.

This enrollment happens once. Future APKs reuse the same Environment secrets and signer.

## Production release request

Normal production publishing is triggered from trusted `develop` by changing exactly:

`.github/release-requests/production.json`

The request contains only:

```json
{
  "source_pr": 123,
  "version_name": "1.0.0",
  "request_id": "prod-unique-request-id"
}
```

The source must be an open same-repository `android/...` PR targeting `develop`, and the latest exact-head `verify`, `screenshot-regression`, and `s24-ultra-target-instrumented` checks must all be successful. The request commit itself may change no other file.

The workflow then:

1. validates the owner-authored guarded request;
2. resolves the immutable validated source PR head;
3. plans the next production versionCode, starting at 10000;
4. tests the exact source with `ANDROID_UPDATE_CHANNEL=production`;
5. patches versionCode/versionName only in the ephemeral CI workspace;
6. builds an unsigned optimized release candidate without production secrets;
7. transfers only that unsigned intermediate with one-day retention;
8. enters `android-production-release`;
9. materializes the protected keystore only under runner temporary storage;
10. verifies its certificate SHA-256 against the enrolled fingerprint;
11. signs and verifies package/version/signer locally;
12. create-only uploads the signed APK to private Supabase Storage;
13. re-reads the exact private bytes and verifies SHA-256 and size;
14. writes release metadata last;
15. reconciles ambiguous writes instead of blind retrying;
16. removes temporary signing material from the runner.

The production-signed APK is never uploaded to GitHub artifacts or public GitHub Releases.

## First production installation versus future updates

The existing Phase 6 test APK is signed with the stable NON-PROD test identity. Android therefore must not accept the first production-signed APK as an in-place replacement of that test-signed installation.

The one-time transition is:

1. preserve any server-backed state normally;
2. install the accepted lower production build using the newly enrolled production identity, using the required clean-install transition from the test signer if Android requires it;
3. authenticate and establish the normal production session/PIN/CVV state on that production-signed baseline;
4. publish a strictly higher production build signed by the same production identity;
5. perform the authoritative in-place S24 Ultra updater smoke;
6. verify no uninstall/parallel package occurs for that same-signer update and session/PIN/CVV continuity is preserved.

After that same-production-signer smoke passes, future production APKs use normal in-place updates and do not repeat the signing handoff.

## Release boundary

Production signer enrollment and production-channel automation do not by themselves authorize `develop -> main`. Issue #14 remains open until the same-production-signer physical update-continuity smoke succeeds. Only then may the final release candidate/version freeze and repository promotion proceed.
