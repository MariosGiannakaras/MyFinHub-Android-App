# Production signing baseline marker

This file intentionally contains no product behavior or release secret.

Its only purpose is to provide a no-functional-change Android-owned PR source for the first production-signing baseline after the protected production publisher was introduced. Because it lives under `app/`, the normal Android CI plus screenshot-regression and representative S24-target gates run on the exact source used by the protected production release workflow.

Do not place keystores, passwords, signing fingerprints, Supabase server credentials, or other release secrets in this file.

## Current release-source checkpoint — 2026-11-11

D7 source for the owner's explicitly requested all-changes Android production-signed APK candidate is based on **merged develop `4097451c89c107f6469706d5077b437adb057f90`**. It includes three-tab native Home/Activity/Cards and Settings from PR #448, read-only S24 preflight #449, and rc13 reconciliation/test fix #447. This marker changes no runtime behavior; it forces full immutable-head Android CI, screenshot and hosted S24 instrumentation validation for the release candidate source.

The previously published `1.0.0-rc13` / versionCode `10012` originates from older PR #446, so it is **not** the all-changes build and must not be re-labeled as such. A future production versionCode, signing fingerprint, and SHA-256 must come only from the guarded publisher. D7/W6.4 requires **actual physical S24 acceptance**, including investigation of the owner-reported finance-load `HTTP 400 / INVALID_DATA`; D8 final stable promotion is separately gated. No financial data, credentials, keystores, signing secrets or actual release assets are stored here.


## D7 S24 video readiness checkpoint — 2026-11-18

This checkpoint starts from Android `develop` commit `a3f7e28b8bf33d2743145f53fe4191b6f25126b2`,
which includes the validated and integrated Activity recovery PR #484 and all preceding
Android development changes. The marker itself changes no application behavior.

An open, non-draft source PR with successful checks on its immutable final head
is required by `scripts/phase6_release_source_gate.py` before a separately
owner-authorized protected production publisher may sign a newer candidate.
This PR additionally fixes the Quick Entry date-picker/keyboard visibility
race on the hosted S24 emulator: clear the amount field focus, request IME
hide, and open the calendar only when IME insets reach zero. The existing
instrumented visible-title test retains its assertion and waits a bounded
five seconds for asynchronous dialog layout. A separate Budget Save test now
uses its real Compose button action rather than Espresso's unstable window-
focus-dependent keyboard close; its Save state and once-only checks remain.
Both corrections require exact-final-head UI validation before integration.
No signed APK is published, and no Play Protect clearance or owner S24 physical
acceptance is inferred from this work.

The device recording and privacy-safe acceptance contract is
`docs/D7_S24_VIDEO_VALIDATION.md`. Its release/source/version fields must
be filled only from a future actually published and independently verified APK,
never by guessing from this marker. Production finance writes and signing
material are explicitly outside this UI/device-readiness checkpoint.

## D7 latest integrated source staged for an owner-tested signed candidate — 2026-11-24

After exact-head six-check verification and squash merge of PR #530, Android `develop` is `86fdae72e9df1ae7c23230523008fe6b77ee55a4`, with its post-merge Android CI and Project Tracking passing. The last published same-signer rc20/10019 tag predates 37 commits of integrated Android corrections; it cannot be used to test those source changes on the real S24 Ultra.

`android/d7-s24-latest-source-candidate-20261124` is a **source-only, no-functional-change** PR checkpoint pinned to that integrated source. The exact final head must pass `tracking-verify`, `android-verify`, `screenshot-regression`, both S24 emulator shards and `s24-ultra-target-instrumented`. **Leave the PR open and non-draft after green checks:** the separate protected production source gate requires a currently open same-repository `android/` PR targeting `develop`. Do not merge or close before an expressly authorized publisher consumes it.

This checkpoint does not sign or publish APK/AAB, request a version number, write `.github/release-requests/production.json`, dispatch a workflow, modify finance data, change signer, or claim real-device acceptance. A new owner-test candidate would need a separately owner-authorized protected publisher and an honest disposition of the outstanding pre-APK D7 audit blockers. A successfully published prerelease is **a prerequisite for** new-source physical acceptance, not equivalent to W6.4/D7 approval, Play Protect clearance or D8 stable promotion. Preserve the previous installed encrypted cache and never uninstall or reset data as a test shortcut.
