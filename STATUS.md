# MyFinHub Android status

## 2026-08-27 — Phase 2C native frontend parity

Phase 2C frontend parity is implemented across the native Android app. The nine tracked workflows in issue #30 are covered by Plan PR #31, Money PR #32 and Home/Utilities PR #33.

### Phase 2C frontend state

- Home Smart Review / Needs Attention drill-down is implemented as nested native Compose UI.
- Money savings, loan/installment and lending/receivable detail/management flows extend the previous overview-only surfaces.
- Plan recurring/scheduled detail and edit flows plus category budget/rule/forecast UI are implemented.
- Android Settings, import/backup UI shell with explicit destructive confirmation, and privacy-safe Undo/Redo / Change History are implemented.
- The five top-level destinations remain Home, Activity, Money, Plan and Insights; new workflows are nested rather than promoted to new top-level destinations.
- Frontend-local deterministic state is used where canonical persistence is not yet available. This does not mark the separate backup/import client boundary or `/api/card-secrets` integration as complete.
- Compact Home exposes the Utilities entry points and the compact navigation path is covered by instrumentation.
- Real app screenshot references for the affected Home and Utilities surfaces were rendered by the official screenshot-test path, visually inspected, and the stale Home baselines were replaced with the validated references.
- Compact, large-font, foldable/tablet and screenshot-regression validation remains the merge gate for PR #33; no production signing or release artifact is part of this phase.

## 2026-08-23 — Production canonical product integration

The Android repository has completed the native foundation, representative product UI, production auth shell and canonical API/sync foundation. PR #20 implements the production bridge from the validated authenticated session to the existing server-authoritative MyFinHub finance state.

### Integrated baseline

- Kotlin + Jetpack Compose + Material 3/Adaptive, single activity, Navigation 3 and ViewModel/StateFlow/UDF.
- compileSdk 37, targetSdk 36, minSdk 26, AGP 9.3.0, Gradle 9.7.0 and Java 17.
- Five top-level mobile destinations retained by ADR-0002: Home, Activity, Money, Plan and Insights.
- Real product screenshot references cover Home, Phase 2B flows and native auth states; placeholder screenshots are not approved product evidence.
- PR UI quality gate runs strict screenshot regression plus compact API 35 instrumentation. Foldable/tablet managed-device validation remains available through workflow dispatch.

### Completed product work

- Phase 1 Android bootstrap merged through PR #5.
- Phase 2A Home merged through PR #9.
- Phase 2B Activity, Quick Entry, Money, Plan and Insights merged through PR #17 as `eefcdaf03327a09835cd8547b688879f7a5b49ab`; issue #12 is complete.
- Native auth/session foundation merged through PR #11.
- Canonical `/api/data` bearer/revision sync foundation merged through PR #18 as `97e1578b83a1f191562c37bd2eb2038fce08a3ab`.
- User-facing auth/local-unlock shell merged through PR #19 as `447abf20044f146a146bdd68dcca0c78c3757689`.
- PR #20 implements authenticated canonical product state, real screen projections/writes, conflict UX and finance-state logout clearing.

### Production backend/auth state

- The MyFinHub native bearer contract is deployed in production through MyFinHub v1.2.0.
- Android uses the production MyFinHub API base and public Supabase client configuration without asking the user for infrastructure keys.
- Email/password and exact six-digit TOTP authentication are implemented.
- AAL2 session material is encrypted at rest with Android Keystore-backed cryptography.
- Routine relaunch is biometric-first with a local PIN fallback. The PIN is local unlock only and never substitutes for server authorization or TOTP/AAL2.
- PIN retry throttling is persistent and escalates 30s → 2m → 10m → 1h.
- After biometric/PIN success, the stored server session is validated/refreshed before the product shell becomes Ready. Invalid/revoked sessions return to normal login.
- Production `MainActivity` is auth-gated. Product/auth test hosts exist only in the debug source set and are non-exported.
- Real-device production Auth/API validation is intentionally deferred to the final Phase 6 checkpoint.

### Canonical finance-data state

Merged PR #18 provides the lossless canonical document, bearer/revision API client and in-memory `FinanceRepository`. PR #20 builds the product layer on top of that foundation:

- validated AAL2 `AuthSession` is required before finance data can load;
- production Ready auth loads canonical `/api/data` before exposing the product shell;
- server remains the source of truth and loaded state remains revision-tagged in memory only;
- Home, Activity, Money, Plan and Insights are projected from canonical seed/state data;
- balances follow snapshot + legacy mutable delta + canonical event-leg semantics;
- Quick Entry writes expense, transfer, card-payment and cent-exact split events;
- Activity edits use explicit canonical note/category saves rather than per-keystroke writes;
- Plan writes overall monthly budgets through the same revision-aware canonical state;
- failed mutations retain a replayable local intent and 409 conflicts require an explicit replay-over-latest or discard choice;
- finance 401/403 rejection clears finance state and returns through the normal auth logout/login path;
- logout/session removal clears in-memory finance state;
- MockWebServer and fail-closed tests cover bearer/no-cookie GET/PUT, `If-Match`, 409, 401 and malformed successful responses;
- raw unknown canonical fields remain preserved across Android mutations;
- no canonical Room/SQLite database and no sensitive HTTP logging are introduced.

### Security boundaries

- Never log passwords, PINs, TOTP, bearer/refresh tokens, FinanceData, PAN, expiry or CVV.
- PAN/expiry remain server-vault data behind owner+AAL2 `/api/card-secrets` access.
- CVV will be device-local Android Keystore AES-GCM only and will never be synchronized, logged or backed up.
- No service-role/server-vault secret, `CARD_VAULT_KEY`, signing password or GitHub token belongs in source or the APK.
- No signed APK is generated during routine development.

### Remaining path

1. Finish issue #15 with backup/import client boundaries and `/api/card-secrets` client boundaries where applicable; the Phase 2C import/backup screen is frontend-only and does not complete these integration boundaries.
2. Complete card-secret/CVV, secure-screen, performance, R8, profile/benchmark and accessibility hardening under issue #13.
3. Complete production smoke, release configuration and final Android Studio/physical-device/signing handoff under issue #14.

Android Studio remains unnecessary for the user until the final handoff. GitHub CI and emulators remain the development validation environment.
