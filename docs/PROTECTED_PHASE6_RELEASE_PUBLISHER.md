# Protected Phase 6 private release publisher

Tracker: #63.

## Purpose

This workflow removes the manual Supabase Dashboard APK-upload step for future `phase6-test` Android builds while preserving the private updater and privileged publishing boundary.

It does **not** authorize production signing, a production publisher, a public APK, or Android `develop -> main` promotion.

## Trigger model

The repository default branch is `main`, while test-release automation intentionally remains on Android `develop` until physical acceptance. GitHub only exposes `workflow_dispatch` when the workflow file exists on the default branch, so the normal pre-production trigger is a guarded release-request file on `develop` rather than a GitHub Actions button.

A release request is a commit that changes exactly:

`.github/release-requests/phase6-test.json`

The request contains only:

- `source_pr`: the validated open Android PR to publish;
- `request_id`: a non-sensitive unique request marker.

The request gate requires the push to target `develop`, be initiated by repository owner `MariosGiannakaras`, and contain no other changed file. Any product-code or unrelated change in the same push fails closed.

The existing `workflow_dispatch` trigger remains for a future repository state where the workflow is present on the default branch; it is not required for the current Phase 6 test flow.

## Source and release gates

The source PR is accepted only when:

- it is open and not draft;
- it targets Android `develop`;
- it comes from this same Android repository, not a fork;
- its branch is Android-owned (`android/...`);
- its latest exact-head `verify`, `screenshot-regression`, and `s24-ultra-target-instrumented` checks are successful.

The workflow derives the next `phase6-test` versionCode/versionName/path from the private feed. It never asks the operator to copy a source SHA or choose a version number.

The build job has no Supabase write credential. It checks out only the validated PR head, tests it with `ANDROID_UPDATE_CHANNEL=phase6-test`, applies the derived test version only in the CI workspace, builds an unsigned release APK, and transfers only that unsigned intermediate with one-day retention.

Protected planning and publishing use GitHub Environment `android-apk-release`. That Environment must allow only branch `develop` and contain Environment secret `SUPABASE_RELEASE_PUBLISH_KEY` holding the dedicated Supabase server-side secret key.

The privileged job signs with the pinned stable AOSP development test identity used for Phase 6 test builds, verifies package/version/signer locally, uploads only to the private `android-releases` bucket, re-reads exact bytes, verifies SHA-256 and size, then inserts release metadata last.

The publisher is hard-locked to:

- Supabase host `ahsukppxwaiagampsuzb.supabase.co`;
- bucket `android-releases`;
- channel `phase6-test`;
- path `phase6-test/<versionCode>/MyFinHub-Phase6-<versionCode>.apk`;
- version name `0.1.0-phase6.<versionCode - 6000>`.

There is no production channel or production signing input.

## One-time setup

The repository owner creates a dedicated Supabase secret key for the Android test publisher and stores it directly in GitHub Environment `android-apk-release` as `SUPABASE_RELEASE_PUBLISH_KEY`. The Environment is restricted to `develop`.

The credential must never be placed in chat, Issues, PRs, repository files, Android resources, Gradle properties, workflow inputs, logs, or the APK.

## Normal future operation

After the one-time secret setup, no APK, SHA, version number, or Supabase metadata is handled manually.

When an accepted Android PR is ready for a private test release, update only `.github/release-requests/phase6-test.json` on `develop`. The protected workflow then:

1. validates the guarded release request;
2. resolves and validates the exact PR head;
3. verifies latest exact-head Android CI/UI checks;
4. derives the next private test version;
5. reruns unit tests under `phase6-test`;
6. builds and validates an unsigned candidate;
7. signs with the pinned NON-PROD identity;
8. verifies package/version/signer;
9. reconciles private Storage before writing;
10. uploads only if the expected object is absent;
11. re-downloads and verifies exact SHA-256/size;
12. inserts metadata last and performs final reconciliation.

The owner then updates from inside MyFinHub on the supported S24 Ultra. No manual APK upload or APK installation is part of the flow.

## Ambiguous writes and reruns

No ambiguous write is retried blindly. Storage and metadata writes are reconciled first. A GitHub run ID is stored only as a non-sensitive release correlation marker so a rerun reuses the same planned version instead of creating another release. Any mismatched bytes or metadata fail closed.

## Validation

The permanent publisher test suite covers fresh publish ordering, exact-object resume, ambiguous Storage and metadata reconciliation, mismatches, idempotent reruns, locked path/version derivation, modern Supabase secret-key headers, PR source gating, and guarded release-request parsing.

## Production boundary

A future production publisher requires a separate protected environment, separate long-lived production signing identity, explicit production-signing authorization, physical same-signer update continuity, and the normal Android release-promotion decision. Nothing here creates or authorizes that path.
