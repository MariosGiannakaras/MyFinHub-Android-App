# Production signing baseline marker

This document contains **no product code, release signing material or secrets**.

This Android-owned `app/` marker is the release-source-only change that activates the existing Android CI, screenshot-regression and both hosted S24 emulator shards on an **exact immutable PR head**. It is not a production release request and is never a replacement for the protected signer or the owner's physical Galaxy S24 Ultra acceptance.

## Candidate: 1.0.0-rc13 — source preparation only (2026-11-10)

- Base: Android `develop` commit `ed973a31d9947c05706e9cb57125a7f3b8025fa4` after merged native W1–W5 checkpoint #444 and 29 golden-mirror visual evidence #445.
- This PR exists solely to validate the latest integrated software as a **potential** same-package, same-enrolled-production-signer, in-place-upgrade candidate above immutable `1.0.0-rc12` / versionCode 10011.
- `1.0.0-rc13` is a proposed versionName, not an already published artifact or an asserted versionCode. The protected publisher must obtain a strictly increasing versionCode and verify signer, package, checksums and create-only publication.
- Keep this source PR **open and unmerged** while the publisher reads its exact validated head. The trusted `develop` release workflow, not this marker or ordinary CI, may sign and publish when its owner actor/environment gates approve.
- Do not uninstall, clear data, install a test/debug-signed build, manipulate live finance data, or claim D7 physical signoff from hosted tests.
- W6.4/D7 requires explicit in-place owner S24 Ultra continuity and functional acceptance after a newer verified candidate exists. D8 is separately gated final release/promotion.

This no-functional-change marker deliberately triggers the complete Android source validation suite. Do not commit keystores, signing fingerprints, passwords, credentials, Supabase secrets, finance data, APK/AAB binaries or account information here.
