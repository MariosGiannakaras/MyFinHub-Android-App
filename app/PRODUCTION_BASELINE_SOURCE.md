# Production baseline source marker

This Android-owned PR exists only to provide an immutable, fully validated source head for the first protected production-signed baseline.

It does not change application behavior, resources, signing configuration, updater policy, or release version.

The same PR also pins the screenshot-only reference date used by existing Quick Entry and Account Detail visual fixtures, so the already-approved canonical screenshots remain reproducible regardless of the CI runner date. Production relative-date behavior continues to use the real current date by default.
