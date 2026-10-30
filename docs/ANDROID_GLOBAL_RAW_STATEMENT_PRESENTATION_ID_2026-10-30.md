# W3.1 — full raw canonical credit statement identity in Card Detail (30 Oct 2026)

PR #400 introduced a global statement ID count **derived from all projected active-card views**, which blocks collisions between well-formed active cards but misses malformed credit-statement records omitted by parsing and statements of archived/otherwise hidden cards. PR #402 already uses `canonicalStatementSourceIdCounts` for full physical raw identity at Quick Entry and canonical payment mutation boundaries. The separate Card Detail route must use that same owner-independent source cardinality, never infer authority from a subset.

- `projectCanonicalProduct` populates a nullable `MoneyUiState.creditStatementSourceIdCounts` with all physical canonical source rows (including malformed foreign and archived). Null is reserved for legacy synthetic/UI fixture state, not production.
- `MyFinHubApp` prioritizes raw global cardinality for Card Detail statement expansion and repayment navigation; only legacy/preview state without raw counts keeps its previous projected-view fallback.
- Historic rows and other cards remain unchanged; no invented 0 amount, silently deleted source row, payment, ledger mutation or new endpoint. Earlier UI guarded actions now fail closed for source collisions even when bad/archived rows are hidden.
- Pure Kotlin production projection golden demonstrates a valid active source colliding with a malformed foreign row: count=2 and cannot navigate/pay. Legitimate unique statement still works; archived-only statement remains in global source counts; empty production source is explicitly an empty map.

No Web/backend/schema, finance mutation, main/signer/release, PIN #345 or SMS #146 changes. Broad Web parity stays 1/6 implementations · 11/24 subtasks; physical owner S24 Ultra #149 is independent. Exact-head four-gate CI needed; local Gradle/SDK not claimed.
