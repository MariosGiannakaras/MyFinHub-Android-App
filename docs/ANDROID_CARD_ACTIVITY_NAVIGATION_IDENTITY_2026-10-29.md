# Android historical credit-card event navigation identity — 29 Oct 2026

Both the owner-approved mixed-card inline activity list and optional Card Detail are projections of the same canonical card history. Imported legacy events may have duplicate or blank IDs. While their physical rows, dates and money must stay visible in source order, navigating via a duplicate or blank ID can open a different event and is not allowed.

This Android-only UI/navigation guard derives ID occurrence counts from the **entire selected card history** before paging, permits detail navigation only for a unique nonblank ID, and presents explicit legacy-source warnings with disabled 48dp+ actions otherwise. It does not delete, merge, renumber or alter event source data; does not invent financial values, turn the Cards root into another page or bypass Hide Amounts privacy. This is a safety guard beyond Web's more permissive legacy event navigation behavior; Web/backend remain unchanged. The established statement-linked row guard remains separate.

Unit tests cover missing, whitespace-only, duplicate, corrected and valid IDs; hosted Compose tests cover the same safeguards in the inline and detailed credit UI. Physical S24 owner acceptance remains separate.
