# Production signing baseline marker

This file intentionally contains no product behavior or release secret.

Its current release-source purpose is to provide the exact Android-owned PR source for the private production candidate `1.0.0-rc5` after the hosted-validated post-Phase-6 redesign pass 3 was merged into `develop`.

This marker change is nonfunctional. It does not change finance semantics, UI behavior, backend/API contracts, Supabase access, authentication/security boundaries, offline/reconcile behavior, updater behavior, package identity, version declarations, or production signing. The protected production publisher injects the requested version only in its CI workspace and must use the already enrolled production signer.

Because this file lives under `app/`, the normal Android CI plus screenshot-regression and representative S24-target gates run on the exact source referenced by the protected production release workflow.

Do not place keystores, passwords, signing fingerprints, Supabase server credentials, private APK URLs, or other release secrets in this file.
