# Native Android D5 Plan and Insights integrity checkpoint — 2026-10-30

The existing Android app's Plan, Forecast, Budget, Savings, Loans, Lending, Recurring/Scheduled and Insights surfaces remain authoritative phone-first Compose screens. Do not recreate the Web desktop interface.

**Feature batch:** The existing Insights formatter can display imported NaN/∞ as a financial amount, use NaN percentage values as misleading 0%, and attempt to draw bars using invalid trend data. This batch uses a single display-only function set to render invalid currency and percentages explicitly, rejects invalid category shares/progress, and withholds a corrupt monthly chart. All existing Hide Amounts controls still mask the resulting labels. Valid ratios, values, large-font and phone layouts remain as before.

The existing Plan→Forecast and Plan→Budget routes are now covered by a new full-app navigation Compose acceptance test; source transaction mutations remain unchanged. Unit tests cover invalid/overflow ratio and currency values and chart safety; hosted Compose covers operational credit/financial reports, privacy and the two Plan user journeys.

**Progress:** Native D1–D4 software integration complete **4/8** after #411's exact-head four-gate PASS and squash merge `e52d7e402b77e99ec8233e1b5369da998a80118d`. D5 remains in progress pending comprehensive native savings/loans/scheduled obligations/report acceptance. D6 security/settings/offline/backup, D7 owner physical Galaxy S24 #149, D8 signed deployment remain open. Historic W1–W6 Web parity accounting 1/6 implementation groups · 11/24 subtasks is not a requirement to copy desktop UI.

No change to canonical financial events, persistence, Web/backend/schema, SMS import #146, global native PIN #345, production signing or release. Merge only with Project Tracking, Android CI, screenshot regression, hosted S24 all SUCCESS on the immutable feature head.
