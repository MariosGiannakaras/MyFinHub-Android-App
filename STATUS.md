# MyFinHub Android status

## 2026-08-22 — Phase 1 implementation in progress

Phase 0 research is complete: PR #2 was squash-merged into `develop` at `f388e4f3f44e3b3c539fd957a6bb65261fbb4e97`, and issue #1 is closed as completed.

Implementation is tracked by issue #3 on `feature/android-bootstrap`; draft PR #5 targets `develop`. The backend native-client dependency is tracked separately by issue #4 so the Android client cannot bypass existing MyFinHub security contracts.

### Repository workflow

- `main` — release/promotion baseline.
- `develop` — primary integration branch.
- `extensions` — long-lived holding branch for future/deferred expansion specs/prototypes; branch-local `EXTENSIONS.md` documents its rules.
- `feature/android-bootstrap` — active Phase 1 implementation branch.
- CI foundation PR #6 and SDK-tool correction PR #7 were squash-merged into `develop` before the Android implementation is eligible for merge.

### Phase 1 implemented checkpoints

- native application id `app.myfinhub.android`, version `0.1.0` / versionCode 1;
- AGP 9.3.0, Compose compiler 2.3.21, Compose BOM 2026.08.00;
- compileSdk 37, targetSdk 36, minSdk 26, Java/JVM 17;
- AGP 9 built-in Kotlin configuration corrected against current Android guidance;
- single-activity edge-to-edge Jetpack Compose app with Material 3 light/dark theme;
- ViewModel + StateFlow + pure reducer bootstrap state;
- Material 3 Adaptive `NavigationSuiteScaffold` using the Phase 0 five-destination hypothesis; compact and wider windows share one destination model;
- typed `MyFinHubApi` boundary with synthetic-only implementation and no production endpoint/credential;
- Android Keystore AES-GCM encryption primitive plus instrumented synthetic round-trip test source;
- unit/bootstrap Compose test sources;
- README, contribution rules, Issue forms, PR template, TODO/status tracking;
- public-repo-safe Android CI and a narrowly scoped wrapper-bootstrap workflow.

### Current validation gate

The Gradle wrapper binary is not yet committed and no green Android build is being claimed. GitHub commits made through the connected GitHub integration have not dispatched observable Actions runs in this session, so PR #5 remains draft. The repository workflows are still in place for normal GitHub events and are designed to generate the Gradle 9.7.0 wrapper, run `test lint assembleDebug`, then commit only wrapper files.

No user action is requested at this point; unresolved validation remains explicitly unchecked in issue #3 and `TODO.md`.

### Still open in Phase 1

- obtain executable build evidence and commit the generated Gradle wrapper;
- integrate Navigation 3 back stacks once the basic build gate is green;
- add official Compose screenshot tests and build-managed compact/medium/expanded device matrix.

### Security/data state

No production Supabase login, finance API integration, real FinanceData, PAN/expiry/CVV, signing credential or private APK is part of this checkpoint. The Keystore class is a primitive only and does not persist real secrets. No WebView dependency exists.

Direct signed-APK sideloading remains the intended personal distribution model. Developer verification is not a current implementation requirement.
