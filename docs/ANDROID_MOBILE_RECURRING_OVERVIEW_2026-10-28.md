# Android read-only subscriptions and recurring obligations

Owner mobile scope: the **Πλάνο** tab always provides «Πάγια & συνδρομές», including when the canonical source has zero items. This screen is **read only**; subscription creation, cadence changes, pause/resume, rules and other authoring remain Web-only.

- Render the canonical Web management order without re-sorting and retain historical duplicate IDs with unique source index-based Compose keys.
- Show active/paused/stopped lifecycle in Greek, validated localized due dates, and distinct amounts: fixed expected amount vs variable estimate/range. Flag invalid imported variable estimates; no amount is described as paid merely because it is expected.
- When real linked actual monthly payments exist, distinguish them from expected bills, applying the existing global Hide Amounts preference. No synthetic values, updates or backend calls.
- Compact active/inactive filters are local UI only and never alter data. The navigation remains Plan → Read-only overview → Plan.
- Pure unit tests for selection, duplicates, statuses, dates, variable/invalid/privacy semantics; hosted Compose tests for empty and filtered duplicate records and root navigation. Product screenshots require visual review if changed.

Historical wider Web parity acceptance remains 1/6 implementations · 11/24 subtasks until source-evidenced acceptance changes; physical S24 Ultra #149 is separately pending.
