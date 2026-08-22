# MyFinHub Android status

## 2026-08-22 — Phase 1 implementation started

Phase 0 research is complete: PR #2 was squash-merged into `develop` at `f388e4f3f44e3b3c539fd957a6bb65261fbb4e97`, and issue #1 is closed as completed.

Implementation is now tracked by issue #3 on `feature/android-bootstrap`. The backend native-client dependency is tracked separately by issue #4 so the Android client cannot accidentally bypass existing MyFinHub security contracts.

### Repository branches

- `main` — release/promotion baseline.
- `develop` — primary integration branch.
- `extensions` — long-lived holding branch for future/deferred expansion specs/prototypes; not a second integration branch.
- `feature/android-bootstrap` — active Phase 1 implementation branch.

### Phase 1 checkpoint A

The active branch now defines the first native Android source baseline:

- application id `app.myfinhub.android`, version `0.1.0` / versionCode 1;
- AGP 9.3.0, Kotlin/Compose compiler 2.3.21, Compose BOM 2026.08.00;
- compileSdk 37, targetSdk 36, minSdk 26, Java/JVM target 17;
- single-activity Jetpack Compose app with edge-to-edge activity setup;
- Material 3 light/dark theme;
- ViewModel + StateFlow + pure reducer example;
- synthetic bootstrap UI explicitly stating that production finance data is not connected;
- unit test plus initial Compose instrumentation test source;
- README, contribution/branch rules, Issue forms and PR template;
- GitHub Actions definitions for one-time Gradle-wrapper generation and normal PR CI.

The first push intentionally relies on a dedicated bootstrap workflow to generate and commit the Gradle 9.7.0 wrapper from the pinned Gradle distribution, validate `test lint assembleDebug`, and push only wrapper files. This avoids manually sourcing a binary wrapper JAR and keeps bootstrap reproducible inside GitHub.

### Still open in Phase 1

- wrapper bootstrap workflow must complete successfully;
- normal PR CI must then pass against the committed wrapper;
- Navigation 3 / Material 3 Adaptive root scaffold;
- typed fake network boundary;
- Android Keystore abstraction/tests;
- official screenshot testing and build-managed device matrix.

### Security/data state

No production Supabase login, finance API, real FinanceData, PAN/expiry/CVV, signing credential or private APK is part of this checkpoint. No WebView dependency exists.

Direct signed-APK sideloading remains the intended personal distribution model. Developer verification is not a current implementation requirement; it is only a future platform-policy note if device behavior changes.
