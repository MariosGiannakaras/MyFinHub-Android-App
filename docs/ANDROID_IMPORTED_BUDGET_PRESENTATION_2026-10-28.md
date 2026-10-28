# Native Plan budgets: corrupted imported read-only numbers

Following the exact-cent overall draft protection in merged PR #387, Android now validates category and subcategory budget presentation separately. It preserves the Web-canonical source values and original valid math but **never invents a zero limit or finite progress** for invalid imported rows. Positive finite effective limits (including carryover) take precedence; absent effective limits fall back to the shared strict exact-cent parser. Nonfinite spent, overflowed ratios or remaining hide the corresponding progress, retaining the row and showing a visible diagnostic that does not leak a financial amount when Hide Amounts is active.

Projected obligations/remaining and carried values are rendered only when finite. Invalid imported commitment amounts remain visible as diagnostic rows rather than disappearing or displaying NaN/Infinity. There are no direct ledger writes and no Web/API/schema changes.

Unit regressions cover finite canonical semantics, missing/invalid limits, overflow, NaN/Infinity and projected amounts. Hosted Compose checks exercise both invalid category/subcategory source rows and privacy-safe presentation. The owner physical S24 Ultra #149 acceptance remains distinct.
