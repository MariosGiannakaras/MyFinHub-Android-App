# MyFinHub Android status

## 2026-09-02 — Full transaction entry parity implementation complete

Tracker #42 / draft PR #43 implement the retained Android **Νέα κίνηση** flow against the shared canonical MyFinHub accounting contract while preserving a native Samsung Galaxy S24 Ultra mobile UX. The implementation is complete; the PR/tracker remain the authoritative integration state until the final exact-head gates pass and the branch is merged into `develop`.

### Canonical transaction-entry parity

- Android now supports the full retained transaction model: Expense, Income, Transfer, Withdrawal, Saving cash offset, Refund, Lending, Repayment, Card purchase, Card payment, Reconciliation and canonical Split.
- Transaction type selection reveals only fields relevant to that accounting intent. Date, account, card, person, category and subcategory choices are explicit where required instead of silently choosing hidden values.
- Account/card options and expense/income category trees are projected from the real canonical document. Unknown desktop-owned fields remain losslessly preserved by the existing canonical mutation layer.
- Withdrawal is constrained to a cash destination and saving cash offset to a savings destination at the canonical mutation boundary, not only in UI filtering.
- Lending and repayment carry per-person receivable semantics. Repayment is rejected when the named person has no outstanding balance or when the amount exceeds that person's canonical outstanding debt.
- Card purchase/payment use an explicit active credit-card identity. Card payment is rejected when the selected card has no debt or when the amount exceeds its current canonical debt; when canonical bank identity is available the payment account must belong to the same bank, matching desktop behavior.
- Reconciliation records only the difference between the entered real balance and the calculated canonical account balance.
- Split is a true accounting split: each part has its own amount/category/subcategory/label and the parent amount is derived exactly from the parts. The previous people-count split behavior is no longer canonical transaction-entry behavior.
- Dirty transaction drafts require explicit discard confirmation on back navigation.

### Validation and visual evidence

- Domain/mutation tests cover ledger effects, destination constraints, category/subcategory projection, reconciliation deltas, split cent totals, per-person lending debt and per-card debt/overpayment boundaries.
- Representative compact-device instrumentation covers full transaction-entry navigation, dynamic type fields, the scrollable split editor and dirty-draft discard protection. The latest pre-reference implementation run completed all representative UI tests successfully.
- Real Compose candidates for the updated compact transaction form and the new split form were personally inspected. Both were accepted: no clipping/overlap defect was found, and the split editor's continuation below the viewport is intentional scroll behavior.
- A renderer run from the later debt-validation source state produced pixel-identical candidates, and exactly those accepted PNGs are now the canonical screenshot references.
- The source state before screenshot canonicalization passed normal Android CI: benchmark/Baseline Profile tooling build, unit tests, instrumentation compile, lint, debug assembly, optimized unsigned release/R8 analysis and release-manifest/unsigned-APK policy audit.
- A final human-authored `app/**` freeze commit will be used for the exact-head CI/UI acceptance before merge so bot-authored screenshot commits are not treated as the final verification state.

### Preserved boundaries

- Existing issue #27 revision-conflict, offline and write-retry rules remain unchanged: attempted writes are not blindly retried after ambiguous transport failure, and reconnect replay is limited to mutations known never to have been sent after a fresh server reload.
- Samsung Galaxy S24 Ultra remains the sole supported Android target; hosted emulator instrumentation is representative only and does not replace Phase 6 physical Samsung acceptance.
- The separately excluded additional privacy/security audit package remains out of scope.
- No release, production signing key or production-signed APK is part of this workstream.

## 2026-09-02 — Post-redesign resilience, data-integrity and diagnostics hardening complete and merged

The retained native Android product has completed and merged the autonomous resilience/data-integrity/auth-recovery/edge-state/diagnostics work requested after the full-app 2026 redesign. The sole supported Android target remains the owner's **Samsung Galaxy S24 Ultra**; `docs/SUPPORTED_DEVICE.md` remains the permanent device-acceptance contract.

Tracker #39 is complete. The validated implementation was originally reviewed in draft PR #40; because the available connector could not transition that draft out of draft state, #40 was closed without code changes and replaced by ready PR #41 from the exact same validated branch/head. PR #41 was squash-merged into `develop`. Do not reopen this workstream for repeat discovery.

### Completed resilience and integrity work

- Production HTTP policy now has explicit connect/read/write/call timeouts and hidden OkHttp connection retries disabled.
- Safe finance reads use a bounded explicit retry for transient failures; attempted writes are never blindly replayed after an ambiguous transport result.
- Android connectivity has explicit online/offline/unknown state. A mutation can remain pending only when the app knows no write request was sent; reconnect reloads the newest server state before replaying the stable mutation intent.
- Rapid duplicate finance submissions, login/TOTP actions, card-secret operations and duplicate nested navigation pushes are suppressed.
- Finance work is cancelled when auth/user state is cleared or switched.
- Canonical integrity validation rejects malformed known collections, duplicate stable IDs, invalid known dates/months, non-finite/out-of-range money values and invalid revision shapes before server data becomes product state.
- Unknown/desktop-owned canonical fields remain preserved losslessly; empty/first-use canonical data remains valid.
- Transient/offline local-unlock validation retains the recoverable session in a secure locked state. Expired/revoked auth still requires login.
- Explicit logout always clears the encrypted local session boundary even if remote revoke unexpectedly fails.

### S24 Ultra UX and safe diagnostics

- Offline/pending finance recovery has a distinct user-facing state instead of a generic save failure.
- Settings includes safe in-app diagnostics for app/build, public environment/API host, connectivity, API/sync state, session/AAL state, last successful sync and the latest safe diagnostic code.
- Diagnostics never include credentials, access/refresh tokens, user IDs, finance payloads, account/transaction content, PAN, expiry or CVV.
- Empty/first-use data, rapid navigation/actions, long account labels and large positive/negative amounts have dedicated automated coverage for the 412×915 phone target.
- Real Compose screenshot candidates for Diagnostics offline/recovery and Home edge values were personally inspected. The first Home edge fixture was rejected because test-only oversized attention text created an ambiguous FAB overlap; the fixture was corrected and only the clean replacement was accepted as the canonical reference.
- Remaining unrouted Backup/Import/Data Transfer utility remnants were removed; those product surfaces remain intentionally excluded from Android.

### Final validation

The final implementation state passed twice on the same code state, including the replacement ready PR event:

1. normal Android CI: benchmark/Baseline Profile tooling build, unit tests, instrumentation compile, lint, debug assembly, optimized unsigned release/R8 analysis and release-manifest/unsigned-APK policy audit;
2. canonical screenshot regression including the two personally approved new phone references;
3. representative S24 Ultra-target compact-phone instrumentation;
4. zero unresolved review threads and zero supported-device implementation blocker.

The stale Phase 5 draft PR #23 was also closed as superseded by the already completed Phase 5 PR #36, so there is no leftover autonomous Android implementation PR from an older workstream.

### Explicit exclusion

The separately proposed additional privacy/security audit package (clipboard/recent-app/accessibility-secret audit) was intentionally **not implemented**, per product-owner instruction. Existing security boundaries remain in force.

## Phase 6 boundary

`docs/PHASE_6_DEVICE_HANDOFF.md` contains the prepared physical-device checklist and workstation prerequisites. Issue #14 remains the authoritative Phase 6 tracker.

The owner's physical Samsung Galaxy S24 Ultra is required for real production Auth/API validation, Samsung One UI/display/font rendering, device-specific performance and the final release-candidate decision. Signing starts only after that acceptance and explicit product-owner authorization.

Do not create a release, production signing key or production-signed APK before the explicit Phase 6 signing handoff.
