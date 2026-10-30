# W3.1 — credit payoff and rate-history source integrity (30 October 2026)

Android source-only, no finance mutation, release or Web/backend change.

- Previously Card Detail and the advisory calculator selected the latest annual-rate version by unvalidated ISO-text comparison, displayed invalid NaN/Infinity rates verbatim, and on a corrupt imported debt showed a generic payment-input error rather than explaining the unavailable forecast.
- A shared read-only selector parses ISO dates, chooses the latest effective date at or before today, and marks an invalid 0–100% rate or duplicate latest effective-date row as INVALID. It does not invent a replacement older rate. Invalid-date-only histories are clearly invalid rather than absent. Future-only dates produce no current rate.
- Historical versions remain visible; invalid source rate/date data show a warning rather than NaN/Infinity. Valid historic percentages remain unchanged.
- The simulator reuses the already-shipped `creditWorkspaceFinancialSnapshot` to require a finite outstanding balance, before invoking unchanged `estimateCanonicalCreditPayoff`. The hypothetical monthly payment is read-only. Hide Amounts hides the entire forecast and input just as before.
- Pure Kotlin and hosted Compose regressions cover valid/future rate, malformed date, duplicate latest date, missing vs invalid rates, nonfinite/negative percent, nonfinite debt, valid estimate and privacy. Tests authored and checked by final SHA CI, not assumed passing.
- Broad 1/6 · 11/24 parity counters unchanged. Physical owner Galaxy S24 #149 separate; native shared global PIN #345 not enabled. No Web/backend/API/schema, finance writes, signed release, main, permanent card deletion or SMS #146.
