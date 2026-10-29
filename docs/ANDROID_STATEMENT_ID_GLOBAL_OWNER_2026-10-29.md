# Android W3.1 — global credit statement source identity (29 October 2026)

Web creditStatements.ts resolves an explicit statementId by the first physical matching statement record. Historic duplicate IDs across different cards are therefore ambiguous, even if Card Detail sees only one statement for its selected card. Android must not select a potentially foreign financial target.

- Preserve all original statement rows, amounts, per-card projection, order, Hide Amounts, and associated event history. Do not generate IDs or mutate finance records.
- Count source statement IDs across all cards in the current canonical MoneyUiState, not just the selected card. A statement with blank/duplicated/missing global identity remains visible but cannot open its ID-dependent linked expansion or payment action; show the existing source-integrity warning.
- Recheck the global identity count in the Card Detail statement-specific payment callback. Quick Entry must reject duplicate explicit statement options by full ID, never choose first matching record.
- Keep standalone Compose fixtures compatible when the optional full source map is omitted. Unit tests and hosted S24 Compose tests cover cross-card collisions, valid unique statements, and privacy.
- No live finance writes, Web/backend/schema, global PIN #345, SMS #146, production signing, main or release. Physical owner Galaxy S24 #149 remains separate.
