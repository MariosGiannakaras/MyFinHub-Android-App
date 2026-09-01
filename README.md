# MyFinHub Android

Native Android client for MyFinHub. The Android product is implemented independently from the web UI while using the same server-authoritative MyFinHub auth, API and canonical finance contracts.

## Current state

The repository is in Phase 5 performance/release hardening. The native product foundation, mobile product flows, production auth/session shell, canonical finance integration, backup/import/card-secret API boundaries and card-secret/CVV hardening are implemented. Final physical-device production validation and production signing remain intentionally deferred to Phase 6.

- Kotlin + Jetpack Compose
- Material 3 + Material 3 Adaptive
- Navigation 3
- application id: `app.myfinhub.android`
- compileSdk 37 / targetSdk 36 / minSdk 26
- AGP 9.3.0 / Gradle 9.7.0 / Java 17
- no WebView/site wrapper
- server-authoritative MyFinHub API/Supabase source of truth
- public source repository with no server secrets, signing material or private APKs

See `STATUS.md`, `TODO.md`, `docs/MOBILE_DESIGN_CONTRACT.md`, `docs/ANDROID_ARCHITECTURE.md` and the active phase issues for the exact implementation checkpoint.

## Branch model

- `main`: release/promotion baseline
- `develop`: normal integration branch
- `extensions`: future/deferred expansion holding branch only
- short-lived `feature/*`, `fix/*`, `research/*`, `chore/*`: issue-scoped work into `develop`

See `CONTRIBUTING.md` for the full workflow.

## Build and validation contract

The normal non-device validation path is:

```bash
./gradlew test lint assembleDebug
```

Phase 5 also validates benchmark/profile tooling and the optimized unsigned release path:

```bash
./gradlew :benchmark:assembleBenchmark
./gradlew assembleRelease analyzeReleaseR8Config
```

`.github/workflows/android-ci.yml` runs those checks and audits the processed release manifest plus unsigned-APK policy. `.github/workflows/android-ui-quality.yml` runs screenshot regression, compact instrumentation, Pixel Fold/Pixel Tablet adaptive instrumentation and a 150% font accessibility pass. `.github/workflows/android-performance.yml` generates Baseline Profile evidence and runs cold-start/Home/Activity/Quick Entry Macrobenchmarks on an emulator.

The benchmark/profile host exists only in profiling variants and must never appear in the production release manifest.

## Runtime configuration

The app contains only public client configuration needed to reach the deployed MyFinHub API and Supabase project. The end user is never asked to enter Vercel/Supabase project configuration or infrastructure keys. Server-only credentials and vault keys must never be packaged into the Android app.

Real production Auth/API validation on a physical device is a Phase 6 release-candidate gate and must not be inferred from emulator or unit-test results.

## Security and release boundary

Never commit real FinanceData, `.env` files, credentials, JWT/refresh tokens, passwords, PINs, TOTP values, PAN/expiry/CVV, Supabase secret/service-role keys, `CARD_VAULT_KEY`, signing keystores/passwords or private APK binaries.

Routine CI intentionally produces only an unsigned release artifact. A long-lived production signing key and signed APK are created only at the explicit Phase 6 signing handoff, after exact-head release validation.
