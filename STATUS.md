# MyFinHub Android status

## 2026-08-23 — Phase 5 performance/release hardening in progress

The Android repository has completed its architecture/bootstrap, representative product UX, production native-auth shell, canonical finance integration, native backup/import/card-secret API boundaries and card-secret/CVV security hardening. The active PR #23 is completing performance, release optimization and final emulator/accessibility validation before the final Phase 6 handoff.

### Integrated baseline

- Kotlin + Jetpack Compose + Material 3/Adaptive, single activity, Navigation 3 and ViewModel/StateFlow/UDF.
- compileSdk 37, targetSdk 36, minSdk 26, AGP 9.3.0, Gradle 9.7.0 and Java 17.
- Five top-level mobile destinations retained by ADR-0002: Home, Activity, Money, Plan and Insights.
- Product screenshot references cover Home, Phase 2B flows and native auth states; placeholder screenshots are not approved product evidence.
- Production `MainActivity` remains auth-gated. Debug product/auth hosts are non-exported and absent from release.

### Completed product/data/auth work

- Phase 1 bootstrap merged through PR #5.
- Phase 2A Home merged through PR #9.
- Phase 2B Activity, Quick Entry, Money, Plan and Insights merged through PR #17 as `eefcdaf03327a09835cd8547b688879f7a5b49ab`; issue #12 is complete.
- Native auth/session foundation merged through PR #11.
- Canonical `/api/data` bearer/revision sync foundation merged through PR #18 as `97e1578b83a1f191562c37bd2eb2038fce08a3ab`.
- User-facing auth/local-unlock shell merged through PR #19 as `447abf20044f146a146bdd68dcca0c78c3757689`.
- Authenticated canonical product integration merged through PR #20 as `d04dc9d081b36126b2b84ecdff3e398c1070f643`.
- Native backup/import/card-secret API boundaries merged through PR #21 as `32a200f719303aa5d9df709c50d897531d1706e1`; issue #15 is complete.
- Card-secret/CVV/secure-window hardening merged through PR #22 as `c8062f6009b06af0a1b53a43868feda6f335350e`.

### Production backend/auth state

- The Android native bearer contract is deployed in production through MyFinHub v1.2.0 as observed during this Android implementation.
- Android uses the production MyFinHub API base and public Supabase client configuration without asking the end user for infrastructure keys.
- Email/password and exact six-digit TOTP authentication are implemented.
- AAL2 session material is encrypted at rest with Android Keystore-backed cryptography.
- Routine relaunch is biometric-first with a local PIN fallback. The PIN is local unlock only and never substitutes for server authorization or TOTP/AAL2.
- PIN retry throttling is persistent and escalates 30s → 2m → 10m → 1h.
- After biometric/PIN success, the stored server session is validated/refreshed before the product shell becomes Ready. Invalid/revoked sessions return to normal login.
- Real-device production Auth/API validation remains intentionally deferred to the final Phase 6 checkpoint.

### Canonical finance-data state

- Server remains the source of truth; Android keeps revision-tagged canonical finance state in memory and does not introduce a canonical Room/SQLite database.
- Home, Activity, Money, Plan and Insights are projected from canonical seed/state/events data.
- Quick Entry writes expense, transfer, card-payment and cent-exact split events.
- Activity note/category edits and Plan budget writes use revision-aware canonical mutations.
- Failed mutations preserve a replayable local intent; revision conflicts require explicit replay-over-latest or discard.
- Unknown canonical fields are preserved across Android mutations.
- Backup, destructive import and card-secret PAN/expiry API boundaries are implemented with owner+AAL2 and bearer/no-cookie semantics.
- CVV is excluded from every server/API model.

### Card/security state

- PAN/expiry reveal is short-lived and retrieved only after a valid owner+AAL2 session.
- CVV is encrypted device-locally with a dedicated Android Keystore AES-GCM alias and is never sent to the server.
- Sensitive card state is kept outside the canonical finance projection and cleared when the card-detail/auth session lifecycle ends.
- Secret reveal applies scoped screenshot/recent-thumbnail protection.
- Local session/PIN/throttle/CVV DataStores are explicitly excluded from cloud backup and device-to-device transfer in addition to `allowBackup=false`.
- Repository audits found no password/PIN/TOTP/token/FinanceData/PAN/CVV logging path and no server-only secret/signing password material in Android source.

### Active Phase 5 work — PR #23

PR #23 is adding and validating:

- AGP 9.3 release optimization through R8 + optimized resource shrinking;
- unsigned release assembly and release-manifest/debug-leakage policy checks in public CI;
- a dedicated release-like benchmark module and Baseline Profile generator;
- cold-start plus Home/Activity/Quick Entry frame/memory Macrobenchmarks;
- a benchmark-only 500-item Activity data set for representative scroll/memory load;
- compact Pixel 6, Pixel Fold and Pixel Tablet instrumentation using the stable emulator-runner path;
- a 150% system-font instrumentation run;
- an automated screen-reader/TalkBack semantics guard that rejects critical clickable UI without a spoken label.

The checked-in Baseline Profile will be added only after successful device generation and inspection. No production signing key or signed APK is created during Phase 5.

### Remaining path

1. Complete PR #23 and close Phase 5 issue #13 with exact-head CI, release, screenshot, adaptive, accessibility and performance evidence.
2. Complete Phase 6 issue #14: production smoke, version freeze, clean-clone/Android Studio handoff, physical-device Auth/API validation and signing/update-continuity handoff.
3. Final reconciliation gate: the main MyFinHub frontend/backend is changing in parallel. After the user explicitly confirms that parallel implementation is finished, compare the final frontend/backend/API/canonical contract with Android, patch any material delta and revalidate before final sign-off.

Android Studio remains unnecessary for the user until the final handoff. GitHub CI and emulators remain the development validation environment until then.
