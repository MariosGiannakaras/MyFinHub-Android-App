# UI/UX canonical implementation plan

## Batch A — navigation foundations
- system-bar safe layout
- persisted amount visibility in Settings
- deterministic newest-first activity ordering
- day/month activity sections
- long-press contextual actions

## Batch B — canonical transaction entry/editing
- explicit account/category/subcategory selectors sourced from canonical document
- Material date picker
- full event edit mutation for safe canonical fields
- return to origin after local enqueue
- keep pending queue visible centrally, not as a blocking form state

## Batch C — product interactions
- account drill-down to filtered activity
- canonical add-card profile flow without PAN/CVV/expiry in finance JSON
- useful Money detail navigation where canonical data exists

## Batch D — notices and retained polish
- one compact short-lived heads-up surface
- non-sensitive notice history drawer for details
- eliminate duplicate Snackbar/heads-up presentations
- tune online pending grace without weakening NEVER_SENT Undo or NEEDS_REVIEW reconciliation
- final rendered light/dark/large-font evidence and accessibility checks
