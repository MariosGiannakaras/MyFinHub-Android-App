# Android root Activity ledger source identity (29 October 2026)

Within the agreed owner mobile scope, the main transaction ledger is a read-first view of original financial events. Imported legacy records may have missing or duplicated IDs, and selecting one of those IDs must never route to an unrelated transaction.

- Keep the original rows, display titles, amounts, day groups and filters; never invent or alter canonical IDs or financial events.
- Compute identity multiplicity from the entire canonical ledger via `ActivityUiState.ambiguousIds`, including rows filtered out or belonging to another account.
- A row with blank or whitespace ID, or an ID duplicated elsewhere in the ledger, is read-only and exposes a clear source-integrity warning in the row. Disable the row's accessibility click semantics as well as guarding the navigation callback. Shared split disclosure remains read-only as already designed.
- A valid unique nonblank ID retains the original detail navigation; `ActivityUiState.isUniqueMutationTarget` remains the source of truth for read/edit/delete routing.
- Pure unit and hosted Compose tests cover blank/whitespace, duplicate IDs across filtered rows, and unique valid navigation. No Web/backend/schema, production financial writes, SMS import, global PIN security or release changes.
