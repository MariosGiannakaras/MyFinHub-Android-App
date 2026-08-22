# MyFinHub Android

Native Android client for MyFinHub.

## Current state

Phase 1 bootstrap is in progress. The application is intentionally disconnected from production finance data until the native bearer-authentication gate is implemented and verified in the main `MariosGiannakaras/MyFinHub` backend.

## Stack

- Kotlin / Jetpack Compose
- Material 3
- AGP 9.3.0
- Compose BOM 2026.08.00
- compileSdk 37 / targetSdk 36 / minSdk 26
- JDK 17
- Gradle 9.7.0 wrapper (generated and committed by the one-time bootstrap workflow)

The project is a native Android application. It must not introduce a WebView/browser wrapper for MyFinHub.

## Repository model

- `main` — deliberate release/promotion baseline.
- `develop` — normal integration branch.
- `extensions` — long-lived holding branch for explicitly deferred future expansion specifications/prototypes; it is not a substitute for `develop`.
- `feature/*`, `fix/*`, `research/*` — short-lived issue branches, normally based on `develop` and merged through PRs.

See `CONTRIBUTING.md`, `AGENTS.md`, `STATUS.md`, `TODO.md`, and `docs/` before changing implementation contracts.

## Build

After the Gradle wrapper bootstrap commit exists:

```bash
./gradlew test lint assembleDebug
```

CI installs the required Android SDK and runs the same baseline automatically.

## Privacy and secrets

This source repository is public intentionally. Never commit real finance data, credentials, access/refresh tokens, Supabase secret/service-role keys, `CARD_VAULT_KEY`, PAN/expiry/CVV, Android signing keys/passwords, or private APKs.

Synthetic preview/test fixtures only are permitted until explicitly replaced by safe integration boundaries.
