# Android Plan recurring overview: read-only actual payments

Owner requirement: subscriptions and regular payments are visible under **Πλάνο**, but recurrence creation, editing and lifecycle management stay **Web-only**. The mobile overview displays canonical data without issuing finance mutations.

- Show expected subscription amount distinctly from **actual booked payments**. Only `actualPaidThisMonth`, `lastActualPaymentDateIso` and `actualPaymentHistory` describe paid money; none may be fabricated from recurring estimates.
- Display actual payment history in bounded groups of five, expanded on the same scroll surface; no network writes.
- Apply the existing Hide Amounts preference to expected, current-month, last actual and every history amount. Dates remain visible.
- Greek-formatted valid ISO dates and Greek known statuses; malformed imported dates and unknown status strings stay unchanged (no guessed values).
- Duplicate historical recurring IDs must render without LazyColumn key collisions. Read-only screens cannot expose stale legacy recurring editor/actions.
- Verify pure formatter/history bounds and hosted Compose duplicate IDs, expansion and privacy. Physical Galaxy S24 Ultra acceptance #149 is separate. No Web/backend/schema, signing/release/main, SMS import or production ledger changes.
