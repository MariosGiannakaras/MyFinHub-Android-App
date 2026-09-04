# Protected Phase 6 private release publisher

Tracker: #63.

## Purpose

This workflow removes the manual Supabase Dashboard APK-upload step for future `phase6-test` Android builds while preserving the private updater and offline/admin publishing boundary.

It does **not** authorize or implement production signing, a production Android publisher, a public APK, or `develop -> main` promotion.

## Security model

The permanent workflow is `.github/workflows/phase6-protected-release.yml` and must be invoked from the trusted Android `develop` branch.

The only normal release input is the number of an open Android PR. The source gate resolves that PR's immutable head commit itself and accepts it only when all of these are true:

- the PR is open and ready for review, not draft;
- the PR targets Android `develop`;
- the PR comes from this same Android repository, not a fork;
- the source branch is Android-owned (`android/...`);
- the latest exact-head checks are successful for `verify`, `screenshot-regression`, and `s24-ultra-target-instrumented`.

The workflow never asks the operator to copy a commit SHA or choose a versionCode. A protected planning job reads the current private `phase6-test` release feed and derives the next strictly increasing versionCode, canonical version name and immutable Storage path. Fixed workflow concurrency prevents two release runs from planning/publishing concurrently.

The product build job has **no Supabase write credential**. It checks out only the source commit resolved by the trusted source gate, reruns unit tests with `ANDROID_UPDATE_CHANNEL=phase6-test`, applies the derived test-only version inside the CI workspace, and produces an unsigned release APK. Only that unsigned intermediate is transferred between jobs, with one-day retention.

Protected planning and publishing run under GitHub Environment `phase6-test-release`. The Supabase credential is scoped only to the individual Python steps that need the private release feed; checkout, SDK setup, product build and APK signing do not receive it.

The publish job checks out the trusted publisher automation from `develop`, downloads the unsigned intermediate, obtains the pinned public AOSP development testkey, signs and verifies package/version/certificate locally, then invokes `scripts/phase6_release_publisher.py`.

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

Prefer a **dedicated Supabase secret API key** (`sb_secret_...`) created only for this Android release-publisher component. Supabase recommends separate server-side secret keys per backend component because they can be rotated independently. A legacy `service_role` JWT remains compatible only where the project still uses that older key model; do not introduce it when a dedicated secret key can be used instead.

In Supabase Dashboard:

1. Open **Settings → API Keys**.
2. Create a dedicated secret key for the Android `phase6-test` publisher (for example, named `android-phase6-release-publisher`).
3. Copy it directly into the GitHub Environment secret in the next steps. Do not send or paste it through chat, Issues, PRs, source files, workflow inputs, or logs.

In GitHub repository settings:

1. Create Environment `phase6-test-release`.
2. Restrict deployment branches/tags to the trusted `develop` branch. Do not allow arbitrary branches.
3. Add Environment secret `SUPABASE_RELEASE_PUBLISH_KEY` with the dedicated Supabase secret key.
4. Never paste that credential into Issues, PRs, chat, workflow inputs, repository files, Android resources, Gradle properties, logs, or the APK.

Do not add a required-reviewer rule unless a manual approval on every protected planning/publish job is explicitly desired; branch restriction plus the workflow source/CI gates are the normal test-channel boundary.

## Normal future release operation

After the one-time environment setup, no APK file, SHA or version number is handled manually.

From GitHub Actions:

1. Open **Protected Phase 6 Test Release**.
2. Select trusted branch `develop`.
3. Choose **Run workflow**.
4. Enter only the validated Android PR number to publish.

The workflow then performs end to end:

1. resolves the exact PR head and validates PR ownership/state/base;
2. verifies the latest exact-head Android CI/UI checks;
3. reads the private feed and derives the next versionCode/versionName/path;
4. reruns unit tests under `phase6-test`;
5. builds and validates an unsigned release candidate;
6. signs with the pinned NON-PROD Phase 6 test identity;
7. verifies package/version/signer locally;
8. reconciles private Storage before writing;
9. uploads only when the expected object is absent;
10. authenticated re-downloads and verifies exact SHA-256/size;
11. inserts release metadata **last**;
12. performs final metadata reconciliation and writes a non-sensitive workflow summary.

The owner then uses the existing in-app updater on the S24 Ultra. No manual APK upload or installation is part of the flow.

## Ambiguous-write and rerun behavior

The publisher never blindly retries a Storage upload or metadata insert after an ambiguous network/write outcome.

- If upload outcome is ambiguous, it reads the expected private object exactly once. Exact SHA-256 + size allows continuation; missing/mismatched bytes fail the run.
- If metadata insert outcome is ambiguous, it reads the exact `(phase6-test, versionCode)` row exactly once. An exact row allows success; absent/mismatched metadata fails the run.
- A later manually initiated rerun is idempotent when the already-present object/metadata exactly match the candidate. Any mismatch fails closed.

This mirrors the Android canonical mutation rule: reconcile before retrying any write whose outcome is not known.

## Validation

`scripts/test_phase6_release_publisher.py` covers fresh publish ordering, exact-object resume, ambiguous Storage reconciliation, ambiguous metadata reconciliation, mismatched-object failure, mismatched-metadata failure, idempotent already-published behavior, and locked Phase 6 path/version derivation.

`scripts/test_phase6_release_key_headers.py` covers the authentication-header split between modern `sb_secret_...` keys and legacy service-role JWTs.

`scripts/test_phase6_release_source_gate.py` covers accepted same-repository Android PRs and rejects fork, draft, wrong-base, non-Android branch, missing/failed/in-progress latest exact-head checks.

`.github/workflows/release-publisher-tests.yml` runs all publisher/source-gate tests on every relevant PR/push without access to the protected credential.

## Production boundary

A future production publisher requires a separate protected environment, separate long-lived production signing identity, explicit production-signing authorization, physical same-signer update continuity, and the normal Android release-promotion decision. Nothing in this Phase 6 publisher creates or authorizes that path.
