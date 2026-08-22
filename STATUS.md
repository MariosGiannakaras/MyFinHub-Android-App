# MyFinHub Android status

## 2026-08-22 — Phase 1 ready for merge

Phase 0 research is complete: PR #2 was squash-merged into `develop` at `f388e4f3f44e3b3c539fd957a6bb65261fbb4e97`, and issue #1 is closed as completed.

Phase 1 implementation is tracked by issue #3 and PR #5 on `feature/android-bootstrap`. The backend native-client dependency is tracked separately by issue #4. Phase 2A Home implementation is scoped in issue #8 and starts only after PR #5 is merged.

### Repository workflow

- `main` — release/promotion baseline.
- `develop` — primary integration branch.
- `extensions` — long-lived holding branch for future/deferred expansion specs/prototypes; branch-local `EXTENSIONS.md` documents its rules.
- `feature/android-bootstrap` — Phase 1 implementation branch, now eligible for merge after final review hygiene.
- CI foundation PR #6 and SDK-tool correction PR #7 were squash-merged into `develop` before the Android implementation.

### Phase 1 completed implementation

- native application id `app.myfinhub.android`, version `0.1.0` / versionCode 1;
- AGP 9.3.0, Compose compiler 2.3.21, Compose BOM 2026.08.00;
- compileSdk 37, targetSdk 36, minSdk 26, Java/JVM 17;
- committed Gradle 9.7.0 wrapper;
- Android 17 SDK provisioning using `platforms;android-37.0` + Build Tools `37.0.0`;
- single-activity edge-to-edge Jetpack Compose app with Material 3 light/dark baseline;
- Material 3 Adaptive `NavigationSuiteScaffold` with the five-destination prototype IA;
- Navigation 3 stable 1.1.6 with a persistent independent back stack per top-level destination;
- ViewModel + StateFlow + pure reducer bootstrap state;
- typed `MyFinHubApi` boundary with synthetic-only implementation and no production endpoint/credential;
- Android Keystore AES-GCM primitive plus instrumented synthetic round-trip test;
- unit and Compose instrumentation tests;
- official Compose Preview Screenshot Testing `0.0.1-alpha15` renderer/validation infrastructure;
- optional Gradle Managed Device definitions for Pixel 6, Pixel Fold and Pixel Tablet API 36 classes;
- required compact PR instrumentation through pinned `ReactiveCircus/android-emulator-runner` commit `a421e43855164a8197daf9d8d40fe71c6996bb0d`, API 35/default/x86_64 Pixel 6, running `connectedDebugAndroidTest`;
- full adaptive managed-device matrix available through explicit workflow dispatch;
- repository README, contribution/branch rules, Issue forms, PR template, TODO/status tracking;
- public-repo-safe CI with immutable Action SHA pins and no signing/release secrets.

### UI screenshot policy

Bootstrap/placeholder golden PNGs were removed before Phase 1 merge. Screenshot infrastructure remains active as an internal renderer smoke test, but placeholder renders are not treated as approved product UI and are not surfaced to the user.

The first committed visual-regression references will be generated from the real Home product screen in issue #8: compact light, compact 150% font scale, and expanded dark. Only implemented application screens/flows are shown to the user.

### Executable validation

On cleanup head `21741ff16b9f585e6394cd95b9890555276a90b4`:

- Android CI run #59: `test`, `lint`, and `assembleDebug` completed successfully.
- Android UI Quality run #44: screenshot renderer/validation smoke completed successfully with no committed bootstrap goldens.
- Android UI Quality run #44: compact API 35 instrumentation completed successfully using the pinned emulator runner.

The immediately preceding emulator-runner checkpoint also completed `connectedDebugAndroidTest` with 2 tests, 0 skipped, 0 failed, confirming the runner path independently of the cleanup-only changes.

### Backend native-client gate

The MyFinHub backend native bearer contract was implemented through main-repo issue #196 / PR #197 and squash-merged to MyFinHub `develop` as `53b14e7cde63e7d84e6a552f55c709f2d746f42f` after its required checks passed.

Future Android-related changes to the base `MyFinHub` repository must use a dedicated Android-owned `android/integration-*` branch and remain strictly limited to the minimum Android integration scope. The remaining backend gate is production promotion through the normal MyFinHub release workstream before Android production finance-data integration is enabled.

### Development/run/build handoff

Android Studio is not required from the user during ongoing implementation. GitHub CI/emulators are the primary development validation environment. Android Studio will be installed only when the application reaches the final run/build checkpoint. Routine development will not generate or distribute signed APKs.

### Security/data state

No production Supabase login, real FinanceData, PAN/expiry/CVV, signing credential or signed APK is part of Phase 1. Test/screenshot fixtures are synthetic. The Keystore class is a primitive only and does not persist real secrets. No WebView dependency exists.

Direct signed-APK sideloading remains the intended personal distribution model once release signing begins. Android developer verification is not a current implementation requirement.
