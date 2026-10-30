# Native Wallet credit statement payment — 2026-10-30

Consolidated Android feature batch **draft PR #406**. The full MyFinHub Android app already exists and should not duplicate Web layouts.

- Native Wallet selected credit card now presents a touch-sized **Πληρωμή αυτής της κατάστασης** action immediately under a uniquely attributable, finite positive-payable statement. This is a navigation/prefill action, **never an automatic payment**.
- The Wallet-to-Quick Entry path uses the selected card ID, exact canonical statement ID and remaining amount. Card Detail and Wallet share `canOpenCanonicalStatementPayment`: reject duplicate global physical source IDs, wrong-card foreign rows, blank IDs, missing IDs, nonfinite/paid debt, stale amount; production uses `creditStatementSourceIdCounts` from ALL raw canonical rows. Pending mutation continues to independently revalidate on replay. No display-only amount can authorize mutation.
- Read-only paid history remains visible, invalid sources show an unavailable explanation rather than a false pay button, and Hide Amounts never reveals money. The production callback rechecks the canonical card/statement immediately before Quick Entry navigation.
- Kotlin source-selection regression and hosted Compose callback/privacy/unsafe-source tests are authored. One full four-gate exact-head PR validation is required after this entire batch is committed, rather than per edit.
- A permanent evidence-backed `deployment_progress` 8-gate tracker (D1+D2 completed, D3 active) was added to canonical JSON and generated progress handoffs. The historical Web parity counters are separate. No physical S24 acceptance/release claimed.

Scope excludes Web/backend/schema, live posted finance event, exposing card secrets, native global PIN #345, SMS import #146, signing/main/release. Physical owner S24 #149 must be recorded separately.
