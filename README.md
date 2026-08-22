# MyFinHub Android

Native Android client for MyFinHub.

## Current state

The repository is in Phase 1 bootstrap. The app is intentionally disconnected from production finance data while the Android architecture, security boundary, test harness and GitHub workflow are established.

- Kotlin + Jetpack Compose
- Material 3 + Material 3 Adaptive
- application id: `app.myfinhub.android`
- no WebView/site wrapper
- same future MyFinHub API/Supabase source of truth
- public source repository with no production secrets or private APKs

See `STATUS.md`, `TODO.md`, `docs/MOBILE_DESIGN_CONTRACT.md`, `docs/ANDROID_ARCHITECTURE.md` and issue #3 for the current implementation checkpoint.

## Branch model

- `main`: release/promotion baseline
- `develop`: normal integration branch
- `extensions`: future/deferred expansion holding branch only
- short-lived `feature/*`, `fix/*`, `research/*`, `chore/*`: issue-scoped work into `develop`

See `CONTRIBUTING.md` for the full workflow.

## Local / CI validation contract

Once the Gradle wrapper is committed, the normal baseline is:

```bash
./gradlew test lint assembleDebug
```

UI-quality automation is separately configured for the official Compose Preview Screenshot Testing tool and Gradle Managed Devices:

```bash
./gradlew validateDebugScreenshotTest
./gradlew compactApi36DebugAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

The full foldable/tablet matrix is automated by `.github/workflows/android-ui-quality.yml` through `workflow_dispatch`. Before the first approved screenshot references are committed, that workflow generates checkout-local references only to smoke-test the official renderer; those generated images are not treated as an approved visual baseline.

## Security rule

Never commit real FinanceData, `.env` files, credentials, JWT/refresh tokens, PAN/expiry/CVV, Supabase secret/service-role keys, `CARD_VAULT_KEY`, signing keystores/passwords, or private APK binaries.

Production-data integration remains gated by the reviewed native bearer-auth contract tracked in issue #4.
