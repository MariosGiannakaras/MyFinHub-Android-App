# Phase 6 pending / Undo / updater-test handoff

Tracker: issue #58. Phase 6 authority remains issue #14 and `docs/PHASE_6_DEVICE_HANDOFF.md`.

## Accepted Android state

The hosted correction is complete and ready to merge into `develop`.

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

The final retained state passed one consolidated acceptance run containing:

- benchmark and Baseline Profile tooling builds;
- unit tests, instrumentation compilation, lint and debug assembly;
- optimized unsigned release/R8 verification plus release-manifest/unsigned-APK policy audit;
- real Compose screenshot rendering and canonical validation for pending Activity, Money, Plan and global pending-banner states in light/dark/150% cases as applicable;
- explicit `NEEDS_REVIEW` visual state with no Undo action;
- representative API-35 connected instrumentation/accessibility coverage, 41/41 tests passing.

The accepted screenshot references were committed by the validation workflow and the temporary acceptance workflow removed itself. Subsequent normal PR workflows may show `action_required` with zero jobs solely because that final commit was authored by `github-actions[bot]`; the accepted state itself was already subjected to the full commands above. This handoff commit is intentionally docs-only so the standard PR workflows can run again from a normal user-authored head without changing application behavior or accepted visual references.

## Central updater dependency

The Android-owned central integration is merged to the central MyFinHub `develop` branch. It isolates `production` and `phase6-test` metadata, retains owner+AAL2/RLS authorization, rejects unknown channels, and includes the temporary pre-channel bootstrap needed by the currently installed Phase 6 build.

The live Supabase schema already contains the channel column/constraints and currently has no release metadata row.

The production Android API base is `https://mgfinhub.vercel.app`, which is deployed from central `main`. Central `main` does not yet contain the Android update endpoint/channel bridge. Central repository policy does not authorize promoting unrelated `develop` work merely for this Phase 6 test. Therefore production availability of the narrow updater bridge is a separate explicit release-policy decision.

## Remaining sequence

1. Merge this validated Android correction into Android `develop` after standard exact-head checks are green and review state is clear.
2. Build the next strictly-higher stable non-production APK with the same pinned public AOSP Phase 6 test signer and `ANDROID_UPDATE_CHANNEL=phase6-test`.
3. Publish that APK/metadata only through the private `android-releases` Phase 6 test path; do not use a public GitHub release and do not create production signing material.
4. Make the narrow central updater bridge reachable at the production API endpoint only after the required explicit release-policy authorization.
5. On the physical Samsung Galaxy S24 Ultra, update in place through Settings and validate the new pending/Undo semantics, package-replacement flow and retained session/PIN/CVV continuity.

No production signing key, production-signed APK, version freeze, release, Android `develop -> main`, or central unrelated-workstream promotion is authorized by this handoff.