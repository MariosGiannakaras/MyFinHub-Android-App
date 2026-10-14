# Production signing baseline marker

This file intentionally contains no product behavior or release secret.

Its only purpose is to provide a no-functional-change Android-owned PR source for the first production-signing baseline after the protected production publisher was introduced. Because it lives under `app/`, the normal Android CI plus screenshot-regression and representative S24-target gates run on the exact source used by the protected production release workflow.

Do not place keystores, passwords, signing fingerprints, Supabase server credentials, or other release secrets in this file.

## Current release-source checkpoint

Issue #149 uses this marker for the validated PureVector branding `1.0.0-rc12` production release-source PR. The last accepted signed release remains `1.0.0-rc11` / versionCode `10010`. A strictly increasing versionCode is selected by the trusted publisher. This checkpoint changes no product behavior, app identity, network or storage contract, signing key or release secret. Do not publish without the required exact-head Android CI, Project Tracking, screenshot regression and representative S24-target instrumentation checks.
