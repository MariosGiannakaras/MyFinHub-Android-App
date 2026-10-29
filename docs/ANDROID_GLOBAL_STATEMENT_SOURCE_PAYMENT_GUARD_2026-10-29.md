# Android W3.1 — full canonical statement source ownership in Quick Entry

The production QuickEntryCanonicalProjection passed only statement views for the currently selected card into the UI. That caused a critical coverage gap after PRs #400/#401: cross-card duplicate statement IDs and malformed source rows were not present in the selected-card options list, so UI-only duplicate filtering could not identify them. The Web finance source can contain historic duplicate physical records; no Android edit may reinterpret their ownership.

- Read raw statement ID multiplicity across **all** state.creditStatements physical rows, including malformed rows omitted by views. Do not rewrite, collapse or delete historic records.
- Keep selected-card view scope, valid statement status/date ordering and finite positive balance. Suppress a candidate if its source ID is blank or not globally unique; drop stale selected IDs on state reprojection.
- Shared canonical transaction builder independently rejects an explicitly selected statement whose raw source ID does not resolve globally to a single physical row. Keep genuine unlinked payment and positive unique card repayment; preserve banking/provider and debt limits.
- JVM source projection and mutation tests cover cross-card duplicates, malformed competing record, source update after previous choice and unique positive repayment. Hosted Compose test passes a real canonical projection into the production picker.
- Physical Galaxy S24 Ultra owner acceptance (#149) remains distinct from hosted CI. No Web/backend/schema, production finance mutations, SMS #146, global PIN #345, signing/main/release changes.
