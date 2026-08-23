# MyFinHub Android TODO

## Phase 0 — Research/design foundation

- [x] Research, architecture/security contract, repository workflow and Phase 0 merge.

## Phase 1 — Android project bootstrap

- [x] Kotlin/Compose project, Material 3/Adaptive, Navigation 3 and ViewModel/StateFlow/UDF.
- [x] compileSdk 37 / targetSdk 36 / minSdk 26, AGP 9.3.0, Gradle 9.7.0, Java 17.
- [x] Keystore primitive, tests, screenshot infrastructure, compact instrumentation and adaptive test foundations.
- [x] Public-repo-safe CI and repository contribution/branch documentation.
- [x] Phase 1 merged through PR #5.

## Phase 2 — Representative mobile product UX

- [x] Home compact/expanded product screen and real Home screenshot references — PR #9.
- [x] Activity list/detail/edit/filter/search.
- [x] Quick Entry expense/transfer/card-payment/split prototypes.
- [x] Money/Cards, Plan and Insights mobile flows.
- [x] Insights → Activity drill-down.
- [x] Representative adaptive list-detail layout.
- [x] Large-font, TalkBack semantics and touch-target validation foundation.
- [x] Retain five top-level destinations and record ADR-0002.
- [x] Phase 2B merged through PR #17; issue #12 complete.

## Phase 3 — Production native-client backend gate

- [x] Native Supabase bearer contract with existing web cookie behavior preserved.
- [x] Owner UID + AAL2 + RLS/RPC enforcement.
- [x] `If-Match`/revision conflict and card-secret authorization semantics preserved.
- [x] Backend validation and MyFinHub `develop` merge.
- [x] Promote/deploy through MyFinHub v1.2.0 and verify READY Vercel Production.

Any future Android-owned backend change must use a dedicated `android/integration-*` branch and document why it is required, exact scope, Android feature served, web/desktop impact and handoff.

## Phase 4A — Native auth and local unlock

Tracker: issue #4.

- [x] Supabase password + refresh + exact six-digit TOTP/AAL2 gateway.
- [x] Keystore-backed encrypted session persistence/restoration/logout.
- [x] Biometric-first relaunch with local PIN fallback.
- [x] Keystore-HMAC PIN verifier; no plaintext PIN persistence.
- [x] Persistent escalating PIN retry throttling.
- [x] Mandatory remote session validation/refresh after local unlock.
- [x] Invalid/revoked session clears local session and returns to login.
- [x] Production launcher auth-gated; debug test hosts non-exported and debug-only.
- [x] Auth instrumentation and reviewed Login/PIN/Locked screenshot references.
- [x] Auth shell merged through PR #19 as `447abf20044f146a146bdd68dcca0c78c3757689`.
- [ ] Validate full auth/unlock flow against production on the physical device at Phase 6 handoff.

## Phase 4B — Canonical finance API and product integration

Tracker: issue #15 — complete.

### Foundation merged through PR #18
- [x] Lossless canonical JSON document wrapper retaining unknown fields.
- [x] Bearer-only `/api/data` GET with AAL2 preflight.
- [x] Revision-tagged in-memory `FinanceRepository`; server remains source of truth.
- [x] PUT `/api/data` with `If-Match`.
- [x] Explicit HTTP 409 `REVISION_CONFLICT`; no silent overwrite.
- [x] No canonical Room/SQLite database.
- [x] Bearer/no-cookie, unknown-field and revision tests.

### Core production product integration — PR #20
- [x] Combine seed legacy transactions, deletions, overrides/custom transactions and canonical events in Android projections.
- [x] Derive account balances from snapshots + legacy deltas + event legs.
- [x] Wire real Home projections.
- [x] Wire real Activity projections and canonical note/category edits.
- [x] Wire Quick Entry expense, transfer, card-payment and cent-exact split writes.
- [x] Wire Money accounts/cards/savings/loan/lending projections without exposing secrets.
- [x] Wire Plan recurring/scheduled/budget projections and budget writes.
- [x] Wire Insights monthly/category projections and supporting Activity drill-down.
- [x] Surface loading/retry and explicit revision-conflict UX.
- [x] On finance authorization rejection, clear finance state and return through normal auth logout/login.
- [x] Clear in-memory finance state on logout/session removal.
- [x] Expand malformed/unauthorized, MockWebServer and canonical mutation tests.

### Native API boundaries — PR #21
- [x] Add owner+AAL2 backup boundary.
- [x] Add destructive import boundary with explicit replacement confirmation header.
- [x] Add `/api/card-secrets` POST/PUT/DELETE boundary for PAN/expiry only; no CVV parameter exists.
- [x] Redact sensitive card-secret request/response representations and retain fail-closed parsing.
- [x] Close issue #15.

### Additional parity after core production wiring
- [ ] Smart Review and Needs Attention parity beyond the canonical semantics already consumed by current projections.
- [ ] Savings-specific workflows beyond current overview.
- [ ] Loan/installment management parity beyond current outstanding-balance projection.
- [ ] Lending/receivable management parity beyond current outstanding-balance projection.
- [ ] Recurring/scheduled edit parity.
- [ ] Budget/rule/forecast planning parity beyond representative editor.
- [ ] Settings parity.
- [ ] Import/backup UX where applicable.
- [ ] Privacy-safe Undo/Redo / Change History semantics appropriate to Android.

These broader parity items are not release blockers for the defined Android v0.1 product contract unless the final post-web reconciliation changes that contract.

## Phase 5 — Security/performance/release hardening

Tracker: issue #13.

### Security — merged through PR #22
- [x] Owner+AAL2 server PAN/expiry vault reveal integration.
- [x] Device-local CVV Android Keystore AES-GCM vault; never sync/log/backup/device-transfer.
- [x] Scoped secure-window/recent-thumbnail protection for secret reveal.
- [x] Sensitive-log/source/preview audit.
- [x] Explicit backup and device-transfer exclusions for local session/PIN/throttle/CVV security stores.

### Performance/release hardening — PR #23 in progress
- [ ] Baseline Profile generation and checked-in release profile.
- [ ] Macrobenchmark cold startup/Home/Activity/Quick Entry journeys.
- [ ] Representative recomposition/memory/jank review, including a 500-item benchmark Activity data set.
- [ ] Enable and validate release R8/minification/resource shrinking with no broad keep-everything rules.
- [ ] Final compact/foldable/tablet and 150%-font/accessibility matrix.
- [ ] Screen-reader/TalkBack semantics guard for critical clickable surfaces.
- [ ] Release manifest/network/debug-leakage and unsigned release assembly validation.
- [ ] Validate direct-sideload update continuity assumptions without creating a production signing key yet.

## Phase 6 — Final production/release handoff

Tracker: issue #14.

- [ ] All functional/security/quality work merged to `develop`.
- [ ] Validate production configured build against real Auth/API.
- [ ] Freeze release versionCode/versionName and promote release candidate through `develop -> main` only after gates pass.
- [ ] Document Android Studio/JDK/SDK prerequisites and one-command Gradle validation.
- [ ] Ensure clean clone/import/Gradle sync requires no local project surgery or secret files.
- [ ] Perform final exact-head unit/lint/debug/release/minification and product screenshot/device-matrix checks.
- [ ] User performs the first physical-device run at this final checkpoint.
- [ ] Validate auth → biometric/PIN relaunch → canonical data sync → logout → re-auth on the physical device.
- [ ] Create/preserve one long-lived signing key only when release signing begins; keep key/password outside public repo.
- [ ] Generate a signed APK only when explicitly requested at handoff.
- [ ] Preserve direct signed-APK sideloading as the personal distribution baseline.

### Final post-web reconciliation gate

The main MyFinHub frontend/backend is being changed in parallel and may continue changing after the Android implementation reaches Phase 6 completion.

- [ ] After the user explicitly confirms the parallel web/backend implementation is finished, compare the final web/backend contracts and canonical semantics against Android.
- [ ] Patch and revalidate Android for any material API/auth/canonical/domain delta before final sign-off.
- [ ] Do not assume the backend/frontend contract observed during Android development is the final contract until that confirmation arrives.

Android Studio and signing material remain intentionally unnecessary until the final handoff.
