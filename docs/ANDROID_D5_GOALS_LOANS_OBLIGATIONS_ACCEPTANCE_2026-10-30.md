# Android D5 savings, loans and scheduled obligations — software integration acceptance (2026-10-30)

The MyFinHub Android app keeps its original **native Compose phone surfaces**. Existing Plan, forecast, budgeting, savings, loans, lending, scheduled and recurring features already have specialized financial-source mutation and hosted UI tests. PR #412 merged the real Plan→Forecast/Budget→Reports app route and Insights invalid imported finance protections after four exact-head successes.

This final D5 mobile acceptance batch closes real source/UI gaps:
- Goal edits/deletes require one **unique, nonblank current source ID**; duplicate historic goal IDs remain visible but read-only. A changed/missing source is rechecked on edit submission and an open delete confirmation revokes when blocked or the source changes. Deleting a goal never implies deleting savings money.
- Imported goal percentages and monthly target rates are finite/bounded before rendering; NaN/∞/overflow never appear as believable 0% or 2147483647%, and Hide Amounts still masks sensitive figures. Valid percentage rounding, overfunded goals and legitimate edit workflows remain intact.
- Nonfinite loan debt is explicitly invalid, **never called a completed loan**, and cannot trigger repayment; native euro formatting for imported nonfinite balances is safe. Duplicated loan IDs retain existing read-only behavior, and existing canonical payment validation still governs postings.
- New Kotlin source-identity/percent/repayment regression tests and hosted Compose invalid goal/debt/privacy tests; actual MyFinHubAppContent navigation acceptance for **Plan → Savings → Loans → Scheduled → Recurring**, without posting live financial events. Existing hosted scheduled confirmation, loan payment and savings-goal editor tests remain in the full UI suite.

**Deployment milestone:** D1–D4 software integration accepted at 4/8. D5 moves to completed and D6 to in-progress in this PR's canonical tracker only **conditional upon four successful final-head CI checks plus squash merge**. User-facing integrated count stays 4/8 until then, becomes 5/8 on success. D7 physical Galaxy S24 Ultra owner acceptance issue #149 and D8 signed release are separate and remain pending. Web bookkeeping 1/6 implementations and 11/24 subtasks is not native app completeness.

No Web/backend/schema, real account mutation, SMS import #146, global native PIN #345, main branch, physical installation or signed deployment.
