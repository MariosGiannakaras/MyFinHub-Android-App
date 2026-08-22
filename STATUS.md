# MyFinHub Android status

## 2026-08-22 — Phase 1 implementation in progress

Phase 0 research is complete: PR #2 was squash-merged into `develop` at `f388e4f3f44e3b3c539fd957a6bb65261fbb4e97`, and issue #1 is closed as completed.

Implementation is tracked by issue #3 on `feature/android-bootstrap`; draft PR #5 targets `develop`. The backend native-client dependency is tracked separately by issue #4.

### Repository workflow

- `main` — release/promotion baseline.
- `develop` — primary integration branch.
- `extensions` — long-lived holding branch for future/deferred expansion specs/prototypes; branch-local `EXTENSIONS.md` documents its rules.
- `feature/android-bootstrap` — active Phase 1 implementation branch.
- CI foundation PR #6 and SDK-tool correction PR #7 were squash-merged into `develop` before the Android implementation becomes eligible for merge.

### Phase 1 implemented checkpoints

- native application id `app.myfinhub.android`, version `0.1.0` / versionCode 1;
- AGP 9.3.0, Compose compiler 2.3.21, Compose BOM 2026.08.00;
- compileSdk 37, targetSdk 36, minSdk 26, Java/JVM 17;
- Android 17 SDK provisioning corrected to the minor-versioned `platforms;android-37.0` + Build Tools `37.0.0` packages used by current tooling;
- single-activity edge-to-edge Jetpack Compose app with Material 3 light/dark theme;
- ViewModel + StateFlow + pure reducer bootstrap state;
- Material 3 Adaptive `NavigationSuiteScaffold` using the Phase 0 five-destination hypothesis; compact and wider windows share one destination model;
- typed `MyFinHubApi` boundary with synthetic-only implementation and no production endpoint/credential;
- Android Keystore AES-GCM encryption primitive plus instrumented synthetic round-trip test source;
- unit/bootstrap Compose test sources;
- official Compose Preview Screenshot Testing plugin `0.0.1-alpha15` configured with compact/light, compact 150%-font, and expanded/dark synthetic fixtures;
- Gradle Managed Devices configured for compact `Pixel 6`, foldable `Pixel Fold`, and expanded `Pixel Tablet` API 36 classes;
- `Android UI Quality` GitHub workflow: screenshot renderer/visual-regression path, compact managed-device test on relevant PRs, and full foldable/tablet matrix through `workflow_dispatch`;
- rendered UI PNGs are now uploaded as short-retention GitHub Actions artifacts so screenshots can be reviewed directly during development without a local IDE;
- README, contribution rules, Issue forms, PR template, TODO/status tracking;
- public-repo-safe Android CI with immutable action SHA pins and no release/signing secrets.

### Backend native-client gate

The MyFinHub backend native bearer contract was implemented through main-repo issue #196 / PR #197 and passed exact-head CI, CodeQL, cross-engine, performance and Windows Desktop package validation. PR #197 was squash-merged to MyFinHub `develop` as `53b14e7cde63e7d84e6a552f55c709f2d746f42f`.

The remaining backend gate is production promotion through the normal MyFinHub `develop -> main` release path before the Android client is allowed to use real production finance data. Android issue #4 remains open specifically for that deployment/consumption boundary.

### Current executable validation

GitHub Actions are now dispatching normally from the active Android PR. The Android 17 SDK provisioning step has been proven green with `platforms;android-37.0`, and the workflows are reaching real Gradle/Compose execution.

Current gates remain conservative:

- the Gradle 9.7.0 wrapper is generated in CI when missing but is not yet committed to the repository;
- `test lint assembleDebug` must complete green before the basic build gate is checked off;
- screenshot rendering has executed successfully in earlier runs, but the first reviewed/committed reference set is still an explicit unchecked gate;
- the compact managed-device test must complete green before Phase 1 is closed;
- Navigation 3 stable back-stack integration follows the green basic-build checkpoint.

No green result is claimed until the corresponding workflow has completed successfully on the exact relevant head.

### Development/run/build handoff decision

The user does not need to install Android Studio during ongoing implementation. GitHub CI/emulators/screenshot artifacts are the primary development validation environment.

Android Studio will be installed only when the application reaches the final run/build checkpoint. Routine development will therefore not create or distribute signed APKs. At final handoff, the repository will provide reproducible Gradle/SDK/JDK prerequisites and the first real-device run/build will be performed then. APK signing/build automation can be added at that point only if explicitly useful.

### Security/data state

No production Supabase login, real FinanceData, PAN/expiry/CVV, signing credential or signed APK is part of the current Android branch. Screenshot fixtures are synthetic. The Keystore class is a primitive only and does not persist real secrets. No WebView dependency exists.

Direct signed-APK sideloading remains the intended personal distribution model once release signing begins. Android developer verification is not a current implementation requirement.
