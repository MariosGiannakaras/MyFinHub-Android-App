# Account-scoped history: Android owner mobile enhancement

Account-detail histories are read-only views into the canonical activity items already scoped by the account's stable ID. No new data source, server query, category reassignment or finance mutation.

- When the account history has at least six rows, show the canonical Web ledger free-text search and lightweight type chips (All/Expense/Income/Transfer/Credit payment). Smaller histories retain the compact approved Android layout.
- Search indexes stored note, category, subcategory and exact kind (same `matchesWebLedgerSearch` as the Transactions root), not formatted balances, account name, display-only derived title or hidden PAN/CVV.
- Show accurate displayed/total row counts, retain original source date/ID order, and open original canonical event detail.
- A 48dp+ Clear Filters action appears only while a filter is active. Account-scoped query and type are saveable across Activity recreation but reset on account identity switch.
- Unit and S24 hosted Compose tests cover filter composition, no matches, reset, privacy, per-account reset and restoration. No Web/backend/schema, financial data writes, signing, main or release.
