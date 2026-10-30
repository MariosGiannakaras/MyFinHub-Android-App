# D4: Activity detail actions and imported split part display — 2026-10-30

**Android user journey:** inspect transaction → change counterparty, edit, or delete; retain stable pending-sync and finance source identity when data refreshes between display and action.

- Detail open/edit/save/delete/counterparty callbacks in `MyFinHubApp` reread the **current** `ActivityUiState` target and check ID uniqueness, pendingSync and global mutation blockage. An archived/deleted/mutated source cannot launch an editor, start a delete, relink counterparty or submit stale edits after a new state arrives.
- Detail view revokes an open delete confirmation or counterparty menu when mutation is blocked or the source disappears/pends; confirmation itself checks current UI mutation block. Menus cannot trigger an action after becoming disabled. Keep imported historical tombstone wording unchanged.
- Split-part imported finance display now formats nonfinite numeric amounts as `Μη έγκυρο ποσό` under existing amount-visibility privacy; a valid part retains exact original local-currency display. Source and ledger totals are never changed by this presentation fix.
- Kotlin regression covers unique/duplicate/missing/pending/global blocked sources and split part formatting; hosted Compose checks live invalid amount presentation and open-dialog revocation. No new layout copying from Web, no backend/schema changes or live finance writes.

Deploy remains **2/8** accepted. D3 Cards/Quick Entry and D4 Activity are still in progress; D7 owner physical S24 #149 and D8 signed deploy require separate approval. Historical Web parity **1/6 implementations · 11/24 subtasks**. One final four-gate CI run on the immutable PR head is required before merge.
