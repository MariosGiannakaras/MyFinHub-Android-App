# Phase 6 pending / Undo / updater-test handoff

Tracker: issue #58. Phase 6 authority remains issue #14 and `docs/PHASE_6_DEVICE_HANDOFF.md`.

## Accepted Android state

The hosted pending/Undo correction is complete and merged into Android `develop` as commit `d0067057e617e61112f97382deb6ff35446db77c`.

- Every durable canonical finance mutation remains visibly represented until server confirmation.
- Transaction deletions retain a non-interactive Activity tombstone instead of disappearing optimistically.
- Pending card deactivation remains represented in Money; pending overall-budget changes remain represented in Plan.
- Pending transaction targets cannot be edited/deleted again while their durable mutation is unresolved.
- `NEVER_SENT` work can expose safe Undo; `NEEDS_REVIEW` work never exposes Undo and is never blindly retried.
- Online mutations have a short local Undo grace period before crossing the network write boundary.
- Per-action pending history preserves causal restoration for repeated Undo.
- Fresh-server-first reconciliation, encrypted persistence and ambiguous-write no-blind-retry semantics remain unchanged.
- A stale Plan pending-budget label is cleared after the durable queue is confirmed empty.
- Android updater requests carry an explicit build-time update channel. Normal builds default to `production`; stable Phase 6 test builds use `phase6-test`; unknown/missing configuration fails closed.

## Final hosted evidence

The merged state passed exact-head normal Android acceptance:

- benchmark and Baseline Profile tooling builds;
- unit tests, instrumentation compilation, lint and debug assembly;
- optimized unsigned release/R8 verification plus release-manifest/unsigned-APK policy audit;
- canonical screenshot regression with candidate regeneration skipped;
- representative API-35 S24-target interaction/accessibility instrumentation.

The accepted pending Activity, Money, Plan and global banner references include light/dark/150% cases as applicable, plus explicit `NEEDS_REVIEW` presentation without an Undo action.

## Stable Phase 6 build 6010

A strictly higher same-signer non-production candidate has been built from the merged Android `develop` state and verified by GitHub Actions.

- package: `app.myfinhub.android`
- versionCode: `6010`
- versionName: `0.1.0-phase6.10`
- update channel: `phase6-test`
- private Storage path: `phase6-test/6010/MyFinHub-Phase6-6010.apk`
- signer certificate SHA-256: `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`
- APK SHA-256: `43e630dbf1509bd50143bf8e144158993083667e1ac28d3bb688c35a05adbb20`
- exact size: `15929849` bytes
- signing identity: pinned AOSP public development test key, non-production only

The build workflow passed tests, the `phase6-test` build configuration, strict version increase, package/version checks and signer verification before publishing the temporary private-publish input artifact. The APK has not been inserted into the production update feed and no production signing material exists.

## Supabase private distribution state

The live Supabase project has been re-verified after the 6010 build:

- private bucket `android-releases` exists with APK/octet-stream MIME restrictions and a 300 MiB limit;
- `rheomiq_android_releases` accepts only `production` or `phase6-test` channels;
- `(channel, version_code)` and `storage_path` are unique;
- `phase6-test` rows are constrained to paths beginning with `phase6-test/`;
- SHA-256, size and version constraints are active;
- release metadata and Storage objects expose owner+AAL2 `SELECT` only to authenticated runtime sessions;
- there is no authenticated runtime `INSERT`/`UPDATE`/`DELETE` publishing permission;
- there are currently zero release metadata rows.

This is intentional. Publishing 6010 therefore requires a privileged administrative Storage upload followed by a matching metadata insert. Do not weaken RLS or add a temporary runtime upload policy merely to bypass that administrative boundary.

## Central updater dependency

The Android-owned central integration is merged to central MyFinHub `develop`. It isolates `production` and `phase6-test` metadata, retains owner+AAL2/RLS authorization, rejects unknown channels, and includes the temporary pre-channel bootstrap required by the currently installed Phase 6 build.

The production Android API base remains `https://mgfinhub.vercel.app`, whose current Vercel production deployment is central `main` commit `e31a4b166be825c7ea3eab43435f3c28750a1c74`. That `main` state does not contain `/api/android-update`.

Central repository policy is explicit: `main` is release-only, routine work integrates through `develop`, and direct `main` hotfixes are limited to emergency security/production fixes. Central `develop` is currently 39 commits ahead of `main` and contains substantial unrelated work. Therefore neither a broad `develop -> main` promotion nor a non-emergency direct-main updater hotfix is authorized by the current repository rules.

No existing repository workflow provides the missing privileged Supabase APK upload, and non-main Vercel Git deployments are intentionally disabled. The remaining delivery boundary is therefore an explicit production/release-policy decision plus an authorized administrative private-Storage publish path.

## Remaining sequence

1. Obtain the explicit production/release-policy decision for how the narrow central updater bridge may become reachable at `mgfinhub.vercel.app` without implicitly promoting unrelated central `develop` work.
2. Upload the already verified 6010 APK through an authorized administrative Supabase Storage mechanism to the immutable path `phase6-test/6010/MyFinHub-Phase6-6010.apk`.
3. Re-verify the uploaded object byte size and SHA-256, then insert exactly matching `phase6-test` release metadata; do not use the `production` channel.
4. Verify the live owner+AAL2 `/api/android-update` response and authenticated private APK download end to end.
5. On the physical Samsung Galaxy S24 Ultra, update in place from installed 6009 through Settings and validate package-replacement behavior, the new pending/Undo semantics and retained encrypted session/PIN/CVV continuity.

No production signing key, production-signed APK, version freeze, Android `develop -> main`, central unrelated-workstream promotion or public Android release is authorized by this handoff.