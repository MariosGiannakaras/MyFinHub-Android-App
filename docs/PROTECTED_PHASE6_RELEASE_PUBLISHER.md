# Protected Phase 6 private release publisher

Tracker: #63.

## Purpose

This workflow removes the manual Supabase Dashboard APK-upload step for future `phase6-test` Android builds while preserving the private updater and offline/admin publishing boundary.

It does **not** authorize or implement production signing, a production Android publisher, a public APK, or `develop -> main` promotion.

## Security model

The permanent workflow is `.github/workflows/phase6-protected-release.yml` and must be invoked from the trusted Android `develop` branch.

The workflow accepts an immutable, exact Android source commit SHA plus the next `phase6-test` versionCode. Before any build it requires successful exact-head checks for:

- `verify` (normal Android CI);
- `screenshot-regression`;
- `s24-ultra-target-instrumented`.

The build job has **no Supabase write credential**. It checks out the requested source commit, reruns unit tests with `ANDROID_UPDATE_CHANNEL=phase6-test`, applies the test-only version inside the CI workspace, and produces an unsigned release APK. Only that unsigned intermediate is transferred between jobs, with one-day retention.

The privileged publish job runs under GitHub Environment `phase6-test-release`. It checks out the trusted publisher automation, downloads the unsigned intermediate, obtains the pinned public AOSP development testkey, signs and verifies package/version/certificate locally, then invokes `scripts/phase6_release_publisher.py`.

The stable signed APK is never uploaded as a GitHub Actions artifact or GitHub Release.

The publisher is hard-locked to:

- Supabase host `ahsukppxwaiagampsuzb.supabase.co`;
- private bucket `android-releases`;
- channel `phase6-test`;
- path `phase6-test/<versionCode>/MyFinHub-Phase6-<versionCode>.apk`;
- Phase 6 version name `0.1.0-phase6.<versionCode - 6000>`.

There is no input or code path for `production`.

## One-time GitHub setup

This setup is intentionally manual because the repository must never contain the administrative Supabase credential and the connected GitHub tooling does not expose secret-management APIs.

In GitHub repository settings:

1. Create Environment `phase6-test-release`.
2. Restrict deployment branches/tags to the trusted `develop` branch. Do not allow arbitrary branches.
3. Add Environment secret `SUPABASE_RELEASE_PUBLISH_KEY` using a Supabase administrative secret/service-role credential that can write the private Storage object and release metadata.
4. Never paste that credential into Issues, PRs, chat, workflow inputs, repository files, Android resources, Gradle properties, logs, or the APK.

A required-reviewer gate may be added to the environment if an explicit human approval is preferred for each publish. It is not required for correctness of the publisher itself.

## Normal future release operation

After the one-time environment setup, no APK file is uploaded manually.

From GitHub Actions, run **Protected Phase 6 Test Release** on `develop` with:

- `source_sha`: the exact Android commit already accepted by the required CI/UI checks;
- `version_code`: the next strictly increasing Phase 6 test version (for example `6012`).

The version name and private Storage path are derived, not user supplied.

The workflow then performs end to end:

1. exact-head check verification;
2. unit tests under `phase6-test`;
3. unsigned release build and package/version validation;
4. signing with the pinned NON-PROD Phase 6 test identity;
5. local signer/package/version verification;
6. private Storage preflight/reconciliation;
7. upload if and only if the object is absent;
8. authenticated re-download and exact SHA-256/size verification;
9. release metadata insert **last**;
10. final metadata reconciliation and a non-sensitive workflow summary.

The owner then uses the existing in-app updater on the S24 Ultra. No manual APK installation is part of the flow.

## Ambiguous-write and rerun behavior

The publisher never blindly retries a Storage upload or metadata insert after an ambiguous network/write outcome.

- If upload outcome is ambiguous, it reads the expected private object exactly once. Exact SHA-256 + size allows continuation; missing/mismatched bytes fail the run.
- If metadata insert outcome is ambiguous, it reads the exact `(phase6-test, versionCode)` row exactly once. An exact row allows success; absent/mismatched metadata fails the run.
- A later manually initiated rerun is idempotent when the already-present object/metadata exactly match the candidate. Any mismatch fails closed.

This mirrors the Android canonical mutation rule: reconcile before retrying any write whose outcome is not known.

## Validation

`scripts/test_phase6_release_publisher.py` covers fresh publish ordering, exact-object resume, ambiguous Storage reconciliation, ambiguous metadata reconciliation, mismatched-object failure, mismatched-metadata failure, idempotent already-published behavior, and locked Phase 6 path/version derivation.

`.github/workflows/release-publisher-tests.yml` runs those tests on every relevant PR/push without access to the protected credential.

## Production boundary

A future production publisher requires a separate protected environment, separate long-lived production signing identity, explicit production-signing authorization, physical same-signer update continuity, and the normal Android release-promotion decision. Nothing in this Phase 6 publisher creates or authorizes that path.
