# D4 / W4.4 — Android Activity financial display and summary integrity (2026-10-30)

The existing native Android Activity ledger can display `NaN` or infinity as currency if a historical import holds a nonfinite amount or contribution; a filter may then show corrupted financial totals as seemingly valid numbers. This change is intentionally **read-only**: retain original history and never change event, transfer or backend semantics.

- Activity row, detail and signed-money formatting label nonfinite amounts explicitly as **Μη έγκυρο ποσό**, including invalid event flow contributions.
- Filtered summary detects any invalid visible row amount/contribution or income/expense/net aggregation overflow; retains all rows and count, but withholds untrustworthy income/expense/net subtotal figures in favor of a visible source warning.
- Historical account balance display masks invalid figures under Hide Amounts and never shows `NaN`/infinity.
- Monthly trends use a safe finite and representable percent calculation: invalid/overflow values produce unavailable comparison, not false 0% or an arbitrary integer. Normal finite values remain unchanged.
- Kotlin regression tests exercise invalid and overflow inputs, safe row formatting and finite rate; hosted Compose coverage exercises real native filtered ledger. No new Web UI controls and no live finance write.
- Release milestones remain **2/8 completed**, D3 Cards/Credit/Quick Entry in progress, D4 Activity in progress. Physical Galaxy S24 owner #149 pending. Historical Web parity 1/6 and 11/24 unchanged.

Merge only after one final complete four-gate exact-head CI suite. No signing, release, backend, schema, SMS #146 or global PIN #345.
