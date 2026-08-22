# GitHub build, test, signing, and private APK delivery

Status: Phase 0 delivery design  
Date: 2026-08-22

## Goal

Keep Android research, source, review, testing, packaging, and release automation inside GitHub as far as technically possible. Avoid dependence on Play Store/third-party CI/distribution dashboards.

The source repository is intentionally public to preserve the desired GitHub Actions economics. That changes how signed binaries must be handled.

## Important privacy constraint

A **public source repository is not a private APK distribution channel**.

GitHub documentation states that public repository resources are readable publicly, and workflow artifacts can be retrieved for public resources. Published GitHub Releases in a public repository are also intended for a wider audience.

Therefore:

- Never publish a signed personal MyFinHub APK as a Release asset in this public source repository.
- Never upload a signed personal MyFinHub APK as a retained workflow artifact in this public source repository.
- Release jobs may build/sign the APK on an ephemeral GitHub-hosted runner and then transfer it directly to a **separate private GitHub distribution repository**.
- Public CI may upload sanitized reports/screenshots generated entirely from synthetic fixtures. Debug APK artifacts are unnecessary and should be omitted by default.

References:

- https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts
- https://docs.github.com/en/rest/actions/artifacts
- https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases

## Repository model

### Public source repository

`MariosGiannakaras/MyFinHub-Android-App`

Contains:

- Kotlin/Compose source;
- Gradle wrapper/build configuration;
- synthetic test fixtures;
- documentation/ADRs;
- GitHub Actions workflows;
- screenshots/goldens with synthetic data;
- issues/PRs/status.

Does not contain:

- signing keystore/passwords;
- real finance data;
- auth/session/card secrets;
- signed private release APKs;
- private distribution credentials.

### Private distribution repository

Recommended: a separate private GitHub repository used only as the controlled binary release vault.

Contains private GitHub Releases such as:

- `MyFinHub-Android-v1.0.0.apk`;
- `MyFinHub-Android-v1.0.0.apk.sha256`;
- release notes/version metadata;
- optional signing certificate fingerprint/provenance metadata.

It does **not** need to run Android builds, so making this repository private does not consume the Android source project's public-runner strategy.

The source release workflow uploads directly to this private repository using a fine-grained token stored as a protected GitHub Actions environment secret and scoped only to the distribution repository with the minimum release/content permissions required.

## Branch/release workflow

Mirror the discipline of the main MyFinHub project:

1. Issue defines one coherent unit of work.
2. Short-lived branch from `develop`.
3. PR to `develop` with required CI.
4. Squash merge after checks/review.
5. Deliberate `develop -> main` release PR.
6. Tag a commit already present on `main`, e.g. `android-v1.0.0`.
7. Protected release workflow verifies tag/version/ancestry, builds, signs, verifies, checksums, and uploads to the private distribution repository.

No release is generated from an arbitrary feature branch.

## CI workflow design

### Fast PR checks

Run on pull requests:

- Gradle wrapper validation.
- dependency/security review where GitHub provides it;
- Kotlin compile;
- Android Lint;
- unit tests;
- Compose feature/component tests;
- Compose Preview Screenshot validation using synthetic fixtures;
- debug assembly as a build-integrity check, without uploading the APK.

### Device/interaction checks

Use Gradle/build-managed Android virtual devices so device definitions remain in repository configuration.

Representative matrix should cover behavior rather than many redundant devices:

- compact phone/current stable Android;
- compact/older supported API where relevant;
- medium foldable/tablet-like width;
- expanded tablet window.

Critical instrumented checks:

- root/top-level navigation and per-tab state preservation;
- predictive back and nested detail flow;
- bottom sheets/dialog dismissal/back behavior;
- IME/inset behavior in Quick Entry and complex editors;
- screen-size adaptation/list-detail behavior;
- accessibility semantics for representative flows;
- light/dark + large-font screenshot/reference coverage.

GitHub-hosted Linux runners support hardware acceleration for Android SDK tooling. Build-managed devices can provision/tear down emulator configurations from Gradle.

References:

- https://docs.github.com/en/actions/reference/runners/github-hosted-runners
- https://developer.android.com/studio/test/managed-devices
- https://developer.android.com/training/testing/different-screens/tools

### Screenshot testing

Use the official Compose Preview Screenshot testing tool where suitable. Reference images are committed only when they contain synthetic/non-sensitive fixtures.

Run Gradle validation in CI and upload failure diff reports when tests fail. Screenshot testing is an explicit visual regression gate, not a substitute for behavioral tests.

References:

- https://developer.android.com/training/testing/ui-tests/screenshot
- https://developer.android.com/studio/preview/compose-screenshot-testing

### Performance

Do not put noisy emulator performance thresholds on every PR initially.

Use a scheduled/release performance job for:

- cold/warm startup;
- Home first usable state;
- large transaction-list scrolling;
- Quick Entry open/submit flow;
- representative navigation transitions.

Generate/validate app Baseline Profiles with Macrobenchmark and keep profile source in the repository.

References:

- https://developer.android.com/develop/ui/compose/performance/baseline-profiles
- https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview

## Release signing

Android update continuity depends on the same signing identity being used for future versions. Loss of the signing key can prevent seamless updates to an installed app.

### CI copy

Store the CI signing material only in a protected GitHub Actions release environment, for example:

- `ANDROID_SIGNING_KEYSTORE_B64`;
- `ANDROID_SIGNING_STORE_PASSWORD`;
- `ANDROID_SIGNING_KEY_ALIAS`;
- `ANDROID_SIGNING_KEY_PASSWORD`.

The job reconstructs the keystore only in the runner's temporary workspace, signs, verifies, then deletes/lets the ephemeral runner be destroyed.

GitHub secrets are encrypted and available only to workflows where explicitly referenced, but they are intentionally not recoverable as plaintext from GitHub after creation.

References:

- https://docs.github.com/en/actions/reference/security/secrets
- https://docs.github.com/en/code-security/reference/secret-security/secret-types
- https://docs.github.com/en/actions/reference/security/secure-use

### Required recovery copy

A CI secret must **not** be the only surviving copy of the signing key. Because GitHub Actions secrets are write-only and account/repository mistakes can happen, maintain one offline encrypted recovery copy of the signing keystore and its recovery credentials.

This is the one deliberate non-repository backup requirement. It is not an ongoing external dashboard/process; it is disaster recovery for the identity that Android uses to recognize upgrades.

Never commit an unencrypted keystore even to the private distribution repository.

## Release workflow gates

On `android-v*` tag:

1. Confirm tag commit is contained in `main`.
2. Confirm tag semver matches Gradle `versionName` and monotonic `versionCode` policy.
3. Run full required tests/build checks.
4. Assemble release APK with R8/minification configuration.
5. Reconstruct signing keystore from protected secrets.
6. Sign APK.
7. Run `apksigner verify` and capture expected certificate SHA-256 fingerprint.
8. Compute APK SHA-256 checksum.
9. Verify APK package name/version using Android build tools.
10. Optionally install/upgrade smoke on a build-managed device with synthetic/fake backend configuration where feasible.
11. Upload APK + checksum + release metadata directly to the private GitHub distribution repository.
12. Do not upload the signed APK to the public source repo's artifacts/releases.
13. Record release outcome in GitHub Issue/Release metadata.

## Private distribution authentication

A public-repo `GITHUB_TOKEN` cannot be treated as a general cross-repository credential for a separate private vault.

Use a fine-grained GitHub personal access token or purpose-built GitHub App token scoped to the private distribution repository. The credential is stored as a GitHub Actions environment secret in the public source repository.

The destination repository identifier can also be stored as a secret/variable if keeping its name out of public workflow logs is desirable.

The release workflow must avoid echoing tokens, keystore material, private release URLs, or other secret values.

## Third-party Actions policy

Prefer first-party GitHub actions and direct Gradle/Android commands. If a third-party Action is needed:

- review its source/security posture;
- pin to an immutable commit SHA;
- give the job least-privilege permissions;
- never expose release secrets to untrusted PR/fork code;
- do not use `pull_request_target` to build arbitrary PR code with secrets.

## Reproducibility

Repository-owned files should define:

- Gradle Wrapper version;
- JDK version;
- Android compile/target/min SDK;
- dependency versions/version catalog;
- device matrix;
- lint/static configuration;
- screenshot configs;
- versioning script/check;
- release/checksum scripts;
- CI workflows;
- package/application ID;
- expected release signing certificate fingerprint (public fingerprint only, not key material).

A clean checkout on a GitHub-hosted runner must be sufficient to build/test unsigned/debug variants without private credentials.

## What cannot be fully GitHub-only

### 1. Installing the APK on the physical phone

GitHub can produce and privately host the APK, but the user/device must authorize/install it. A cloud workflow cannot silently install a personal APK onto an unrelated physical Android phone.

### 2. Android developer verification / limited distribution (future-proofing)

As of August 2026 Google has launched Android Developer Console limited-distribution accounts for hobbyist/personal use. Google states global Android developer-verification expansion will occur in 2027 on certified Android devices. Limited distribution supports up to 20 explicitly authorized devices without government-ID verification or a fee, but requires a Google Account, 2-Step Verification, a linked payments profile/legal details, package registration, and a QR/link device authorization handshake.

That Google-console/device handshake cannot be replaced by GitHub Actions.

For Greece in August 2026, the September 30, 2026 first enforcement phase applies to Brazil, Indonesia, Singapore, and Thailand/participating stores, not general direct sideloading in Greece. However the 2027 global rollout should be treated as a future operational dependency.

Alternative: Google's advanced sideload flow or ADB remains available for unregistered apps, but the user experience is intentionally more advanced/frictionful. For one personal device, the free limited-distribution path is the cleaner future option when needed.

References:

- https://developer.android.com/developer-verification/guides
- https://developer.android.com/developer-verification/guides/limited-distribution
- https://developer.android.com/developer-verification/guides/faq

### 3. Signing-key disaster-recovery backup

The active CI copy can live entirely in GitHub Actions secrets, but a durable recoverable backup should exist outside the repository/GitHub secret store. This prevents a GitHub account/repository failure from permanently breaking Android update continuity.

### 4. Final physical-device UX verification

Most behavioral/visual/accessibility checks can run in Actions. A real-device smoke/TalkBack/biometric/installation check remains valuable before important releases because emulator CI cannot perfectly represent hardware biometrics, OEM behavior, or human assistive-technology usability. The checklist and evidence remain tracked in GitHub; the interaction itself occurs on the phone.

## Phase 0 conclusion

The ongoing engineering process can be almost entirely GitHub-native:

- source/research/design: public GitHub repo;
- reviews/status: Issues/PRs;
- CI/device tests/screenshots: GitHub Actions;
- signing: GitHub protected secrets;
- private APK release: separate private GitHub distribution repo.

The only non-GitHub runtime actions are device installation/authorization, future Android Developer Console verification if chosen/required, a safe offline signing-key recovery backup, and final physical-device validation.