# Quick Entry payable credit statement picker integrity — 29 October 2026

Android W3.1. Earlier PR #400 prevents first-match resolution of duplicated statement IDs across cards at Save; however Quick Entry could still offer those ambiguous choices before rejecting them. Imported non-finite and settled/negative statement balances must never become a valid explicit repayment target.

- Source the native statement picker only from the canonical statement options, preserving full-ledger ID uniqueness, exact chosen card, finite positive remaining and nonblank source IDs. Do not invent financial IDs or rewrite source history.
- A missing or ambiguous source remains nonselectable; the optional 'without statement' card payment remains available through the existing Quick Entry flow.
- Revalidate the exact selected statement on Save. Reject NaN/Infinity and balances <= 0.005 before any canonical mutation preview; preserve existing overpayment tolerance/other validation.
- JVM tests cover duplicate across cards, invalid and positive balances, and valid unique Save. Hosted Compose tests cover production picker absence and unique statement choice. No local Gradle run claimed; CI/physical device acceptance independent.
- No Web/backend/schema, production finance writes, SMS #146, global PIN #345, irreversible card deletion, main/signing/release changes.
