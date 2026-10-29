# Card and statement cross-ledger activity identity (29 October 2026)

Android-only W2.4 safety checkpoint. Locally unique imported card activity IDs can collide with another card/account or be absent from the global Activity ledger; Activity Detail itself resolves only globally unique canonical IDs.

- Keep original card rows, financial values, dates and statement links visible and unchanged, with Hide Amounts honored.
- The production Money route passes global canonical activity ID counts into inline credit, Card Detail and statement linked history, while preserving local per-card and per-statement duplicate detection.
- Missing or duplicated full-ledger activity IDs render as non-navigable historical entries with a clear warning. The shared route callbacks also recheck ActivityUiState.isUniqueMutationTarget at navigation time.
- Isolated previews without a global ledger retain historical local-only behavior for fixtures. Pure JVM and hosted Compose tests cover global collisions and valid unique events.
- Physical owner's Galaxy S24 #149 is separate. No finance writes, Web/backend/schema, SMS import, shared PIN #345, signing, main, or release work.
