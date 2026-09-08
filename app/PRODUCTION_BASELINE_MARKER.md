# Production signing baseline marker

This file intentionally contains no product behavior or release secret.

Its only purpose is to provide a no-functional-change Android-owned PR source for the first production-signing baseline after the protected production publisher was introduced. Because it lives under `app/`, the normal Android CI plus screenshot-regression and representative S24-target gates run on the exact source used by the protected production release workflow.

Release-source checkpoint: `1.0.0-rc7` freezes the corrected post-rc6 `develop` state after the owner-rejected rc6 correction pass and final protected release-preparation validation. This marker changes no runtime behavior.

Do not place keystores, passwords, signing fingerprints, Supabase server credentials, or other release secrets in this file.
