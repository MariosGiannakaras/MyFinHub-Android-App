# Android Account Detail — duplicate/blank historical activity IDs

Owner's Accounts drill-down must show every source record even for imported historical rows that have missing, whitespace-only, or duplicated IDs. Such IDs **cannot** be used as LazyColumn item keys or as trustworthy global Activity detail route targets.

Use the original source list position as the stable LazyColumn key (including when searching/filtering, without synthesizing persisted financial IDs). Determine navigability from the **entire canonical ledger**, not just the selected account's filtered subset. Retain original rows and signed values; show a visible warning and a disabled/non-actionable row for ambiguous or missing IDs. Only unique nonblank IDs route to the existing shared Activity detail screen. Independently check canonical global uniqueness at the navigation boundary. Keep amount hiding and other account history operations unchanged.

Tests cover source-position key survival with duplicate/blank values, global cross-account collision, valid unique detail opening and filter state. Android-only: no finance-data repair, backend/Web/APIs, irreversible deletion, SMS import or release.
