# Production release source marker

This file intentionally contains no product behavior or release secret.

Its only purpose is to provide a no-functional-change Android-owned PR source for protected production release validation. Because it lives under `app/`, the normal Android CI plus screenshot-regression and representative S24-target gates run on the exact source used by the protected production release workflow.

Current release-source intent: validate the post-Phase-6 redesigned `develop` state for the `1.0.0-rc3` production candidate. No application behavior, resources, backend/API contract, signing material, updater policy, or production data is changed by this marker update.

Do not place keystores, passwords, signing fingerprints, Supabase server credentials, or other release secrets in this file.
