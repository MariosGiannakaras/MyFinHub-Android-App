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
This PR additionally fixes an observed Quick Entry date-picker/keyboard
visibility issue on the hosted S24 emulator by clearing the amount field's
focus and hiding its soft keyboard before opening the date dialog. This
runtime correction must pass exact-final-head UI validation before integration.
No signed APK is published, and no Play Protect clearance or owner S24 physical
acceptance is inferred from this work.

The device recording and privacy-safe acceptance contract is
`docs/D7_S24_VIDEO_VALIDATION.md`. Its release/source/version fields must
be filled only from a future actually published and independently verified APK,
never by guessing from this marker. Production finance writes and signing
material are explicitly outside this UI/device-readiness checkpoint.
