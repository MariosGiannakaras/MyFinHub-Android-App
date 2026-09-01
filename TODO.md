# MyFinHub Android TODO

## Phase 0 — Research/design foundation

- [x] Research, architecture/security contract, repository workflow and Phase 0 merge.

## Phase 1 — Android project bootstrap

- [x] Kotlin/Compose project, Material 3/Adaptive, Navigation 3 and ViewModel/StateFlow/UDF.
- [x] compileSdk 37 / targetSdk 36 / minSdk 26, AGP 9.3.0, Gradle 9.7.0, Java 17.
- [x] Keystore primitive, tests, screenshot infrastructure, compact instrumentation and adaptive managed-device definitions.
- [x] Public-repo-safe CI and repository contribution/branch documentation.
- [x] Phase 1 merged through PR #5.

## Phase 2 — Representative mobile product UX and frontend parity

- [x] Home compact/expanded product screen and real Home screenshot references — PR #9.
- [x] Activity list/detail/edit/filter/search.
- [x] Quick Entry expense/transfer/card-payment/split flows.
- [x] Money/Cards, Plan and Insights mobile flows.
- [x] Insights → Activity drill-down.
- [x] Representative adaptive list-detail layout.
- [x] Large-font, TalkBack semantics and touch-target validation.
- [x] Retain five top-level destinations and record ADR-0002.
- [x] Phase 2B merged through PR #17; issue #12 complete.
- [x] Plan recurring/scheduled detail/edit plus category budget/rule/forecast frontend parity — PR #31.
- [x] Money savings, loan/installment and lending/receivable frontend parity — PR #32.
- [x] Home Smart Review / Needs Attention, Settings, import/backup UI shell and privacy-safe Change History frontend parity — PR #35.
- [x] Real screenshot rendering/regression plus compact, foldable, tablet and large-font validation for the Phase 2C UI delta.

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
- [x] Auth shell merged through PR #19.
- [ ] Validate full auth/unlock flow against production on the physical device at Phase 6 handoff.

## Phase 4B — Canonical finance API and product integration

Tracker: issue #15.

- [x] Lossless canonical JSON document retaining unknown fields.
- [x] Bearer-only `/api/data` GET with AAL2 preflight.
- [x] Revision-tagged in-memory `FinanceRepository`; server remains source of truth.
- [x] PUT `/api/data` with `If-Match` and explicit 409 conflict handling.
- [x] No canonical Room/SQLite database.
- [x] Real Home, Activity, Quick Entry, Money, Plan and Insights projections/writes over canonical state.
- [x] Loading/retry, authorization rejection and explicit revision-conflict UX.
- [x] Clear in-memory finance state on logout/session removal.
- [x] Typed bearer-only backup/import client boundaries — PR #21.
- [x] Typed owner+AAL2 `/api/card-secrets` PAN/expiry client boundary with no CVV server surface — PR #21.
- [x] MockWebServer/fail-closed coverage for finance, backup/import and card-secret boundaries.

## Phase 5 — Security/performance/release hardening

Tracker: issue #13 complete. Implementation PR #36 merged into `develop`.

- [x] Owner+AAL2 server PAN/expiry vault integration.
- [x] Device-local CVV Android Keystore AES-GCM vault; never sync/log/backup.
- [x] Scoped secure-window/recent-thumbnail protection for secret reveal.
- [x] Sensitive-log/source/preview audit.
- [x] Representative recomposition/large-state review.
- [x] Enable and validate release R8/minification/resource shrinking with narrow keep rules.
- [x] Unsigned release assembly/config validation; no signing secrets or production-signed APK.
- [x] Generate and check in the device-generated Baseline Profile and startup profile.
- [x] Obtain passing exact-head Macrobenchmark evidence for cold startup, Home, Activity and Quick Entry.
- [x] Obtain passing exact-head compact/foldable/tablet and 150% large-font/accessibility matrix evidence.
- [x] Complete final Phase 5 evidence/documentation sync and merge the active hardening PR into `develop`.

## Phase 6 — Final production/release handoff

Tracker: issue #14. This phase remains separate and is not part of the completed autonomous implementation.

- [ ] All functional/security/quality work merged to `develop` and handoff state accepted for Phase 6.
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

Android Studio and signing material are intentionally unnecessary during ongoing development.
