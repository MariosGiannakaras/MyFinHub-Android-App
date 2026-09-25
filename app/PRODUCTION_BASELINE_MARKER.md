# Production signing baseline marker

This file intentionally contains no product behavior or release secret.

Its only purpose is to provide a no-functional-change Android-owned PR source for the first production-signing baseline after the protected production publisher was introduced. Because it lives under `app/`, the normal Android CI plus screenshot-regression and representative S24-target gates run on the exact source used by the protected production release workflow.

Do not place keystores, passwords, signing fingerprints, Supabase server credentials, or other release secrets in this file.

## Current release-source checkpoint

Issue #129 uses this marker to force full exact-head Android CI, screenshot regression and S24-target instrumentation for the post-card-vault `1.0.0-rc11` source. This section changes no product behavior and contains no release secret.
