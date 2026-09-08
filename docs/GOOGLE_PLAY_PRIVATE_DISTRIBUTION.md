# Google Play limited/private distribution

## Decision

For legitimate Google-managed installation without a public Play Store listing, MyFinHub uses **Google Play Internal testing** as the primary limited-distribution mechanism. Internal testing is intended for up to 100 explicitly selected testers and delivers the app through Google Play. If the private tester group later exceeds that limit or needs multiple cohorts, use a **Closed testing** track instead.

Do not use Internal App Sharing as the long-lived release channel. Internal App Sharing is useful for short QA links, but Google re-signs those uploads with an Internal App Sharing key and the links expire. That does not preserve the already-enrolled MyFinHub production signing identity required for in-place updates of the currently installed production package.

Official references:

- https://support.google.com/googleplay/android-developer/answer/9845334
- https://support.google.com/googleplay/android-developer/answer/9844679
- https://support.google.com/googleplay/android-developer/answer/12085295
- https://developer.android.com/studio/publish/app-signing

## Repository build contract

The direct/private MyFinHub release and the Google Play release intentionally use different update behavior while keeping the same package/application ID:

- package/application ID: `app.myfinhub.android`;
- direct/private build: `release`, with the existing owner-only private updater enabled;
- Google Play build: `playRelease`, with MyFinHub direct/self-update UI disabled;
- `playRelease` removes `android.permission.REQUEST_INSTALL_PACKAGES` and `android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION` from its merged manifest;
- Google Play is the only updater for the Play-distributed variant.

This split is required because Google Play policy does not permit a personal-finance app to request `REQUEST_INSTALL_PACKAGES` merely to self-update.

Normal CI must build `bundlePlayRelease` and fail if either installer permission leaks into the Play manifest.

## Signing continuity

MyFinHub already has an enrolled long-lived production signing identity used by the installed private production candidates. Do not create, rotate, replace, expose or commit that key merely to start Google Play distribution.

For the first Play Console enrollment of `app.myfinhub.android`, configure **Play App Signing using the existing MyFinHub app-signing key** so Google-delivered APKs remain signature-compatible with existing direct installations. Google documents an enrollment path for providing an existing app-signing key. The private key transfer/enrollment must be completed through Play Console's supported protected process (for example the PEPK flow shown by Play Console), never by checking the keystore into this repository or exposing it in CI logs/artifacts.

After Play App Signing is enrolled, register a separate upload key if desired/recommended by Play Console. The app-signing identity delivered to devices must remain compatible with the existing MyFinHub package.

## Release artifact

The protected production workflow produces and signs both:

1. a direct APK signed with the enrolled production identity; and
2. a `playRelease` AAB for Google Play Internal testing.

The GitHub Release also contains SHA-256 checksums and safe release metadata. The AAB is not itself installed directly on the phone; upload it to the selected Play testing track.

## One-time Play Console setup

These steps require the owner's Google Play Console account and cannot be completed from repository-only automation without Play Console credentials/access:

1. Create/select the Play Console app for package `app.myfinhub.android`.
2. Enroll in Play App Signing and choose the supported option to provide the existing MyFinHub app-signing key so update identity is preserved.
3. Complete any required app setup/policy declarations for the testing track.
4. Create an **Internal testing** tester list containing the owner/tester Google accounts.
5. Upload the signed `playRelease` AAB from the corresponding GitHub Release.
6. Create/roll out the Internal testing release.
7. Use the Play-provided opt-in/install link on the authorized device/account.
8. Verify Play reports the expected package/version and that an existing same-signer installation can update in place. Treat a required uninstall, package conflict or signer mismatch as a release blocker.

Do not promote to Open testing or Production merely to make installation easier. Internal/Closed testing is the intended non-public distribution surface.

## Future automation

After the one-time Play Console enrollment is complete, Play Developer API automation may be added using a least-privilege service account stored only as a protected GitHub secret/environment credential. Do not add a speculative publishing credential or an unaudited third-party publishing action before the Play Console app and signing enrollment actually exist.
