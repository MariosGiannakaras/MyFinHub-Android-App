# MyFinHub Android status

## 2026-08-23 — Production integration in progress

The Android repository has completed the native foundation, representative product UI, production auth shell and canonical API/sync foundation. The active workstream is now wiring the authenticated product UI to the existing server-authoritative MyFinHub finance state.

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

Merged PR #18 provides:

- lossless raw canonical JSON retention, including unknown fields;
- typed account/event/scheduled/card projections as a foundation;
- bearer-only GET `/api/data`;
- AAL2 preflight;
- PUT `/api/data` with `If-Match`;
- explicit `REVISION_CONFLICT` mapping for HTTP 409;
- an in-memory revision-tagged `FinanceRepository` with the server as source of truth;
- no canonical Room/SQLite database and no sensitive HTTP logging.

The active `feature/canonical-product-integration` workstream must connect the authenticated product shell to this repository, combine legacy seed/custom/override data with canonical events for complete projections, implement real writes and conflict UX, and clear finance state on logout/auth rejection.

### Security boundaries

- Never log passwords, PINs, TOTP, bearer/refresh tokens, FinanceData, PAN, expiry or CVV.
- PAN/expiry remain server-vault data behind owner+AAL2 `/api/card-secrets` access.
- CVV will be device-local Android Keystore AES-GCM only and will never be synchronized, logged or backed up.
- No service-role/server-vault secret, `CARD_VAULT_KEY`, signing password or GitHub token belongs in source or the APK.
- No signed APK is generated during routine development.

### Remaining path

1. Complete canonical product projections/writes and user-visible revision conflict handling under issue #15.
2. Complete card-secret/CVV, secure-screen, performance, R8, profile/benchmark and accessibility hardening under issue #13.
3. Complete production smoke, release configuration and final Android Studio/physical-device/signing handoff under issue #14.

Android Studio remains unnecessary for the user until the final handoff. GitHub CI and emulators remain the development validation environment.