# MyFinHub Android TODO

## Phase 0 — Research/design foundation

- [x] Bootstrap public Android source repository.
- [x] Create `develop` and Phase 0 research branch.
- [x] Research current Android 2026 Compose/Material/adaptive/navigation/accessibility guidance.
- [x] Research finance/productivity examples for translating complex desktop/web capability to mobile.
- [x] Define mobile-first MyFinHub design contract.
- [x] Define Android architecture/security boundary.
- [x] Define GitHub Actions/signing/private-distribution constraints.
- [x] Identify unavoidable device-local steps in advance.
- [x] Review and squash-merge Phase 0 PR #2 into `develop`.

## Phase 1 — Android project bootstrap

Tracker: issue #3. Active implementation PR: #5.

- [x] Create initial Gradle/Kotlin/Compose project with pinned AGP/Kotlin/Compose/JDK/SDK versions.
- [x] Define application/package ID `app.myfinhub.android` and initial `0.1.0` / versionCode 1 policy baseline.
- [x] Configure Material 3 light/dark theme and synthetic bootstrap UI.
- [x] Configure Material 3 Adaptive `NavigationSuiteScaffold` root shell for bottom-bar/rail adaptation.
- [ ] Integrate Navigation 3 stable back stacks after the basic executable build gate is green.
- [x] Configure ViewModel/StateFlow/UDF bootstrap foundation.
- [x] Configure typed `MyFinHubApi` boundary with fake/synthetic implementation first.
- [x] Configure Android Keystore AES-GCM primitive and an instrumented round-trip test source; no real secret storage yet.
- [x] Add unit tests and initial Compose instrumentation test source.
- [x] Configure official Compose Preview Screenshot Testing `0.0.1-alpha15` with compact/light, 150%-font and expanded/dark fixtures.
- [x] Configure Gradle Managed Devices for compact (`Pixel 6`), foldable (`Pixel Fold`) and expanded (`Pixel Tablet`) API 36 classes.
- [x] Add Android UI Quality workflow: host screenshot path, compact PR device test, and full adaptive matrix on explicit workflow dispatch.
- [x] Publish rendered synthetic UI screenshots as short-retention GitHub Actions artifacts so implementation visuals can be reviewed without a local IDE.
- [ ] Generate and commit the first approved screenshot reference images; before references exist, CI only smoke-tests the renderer then validates the generated checkout-local baseline.
- [x] Add public-repo-safe PR CI definition; no release secrets or signed APK artifacts.
- [x] Correct Android 17 SDK provisioning to the minor-versioned `platforms;android-37.0` / Build Tools `37.0.0` packages used by current tooling.
- [ ] Generate/commit Gradle 9.7.0 wrapper and confirm `test lint assembleDebug` passes.
- [x] Add repository README, contribution/branch rules, Issue templates and PR template.
- [x] Create long-lived `extensions` branch and branch-local `EXTENSIONS.md` for explicitly deferred future expansion work.

## Phase 2 — Representative mobile prototypes

Use synthetic finance data only until the backend native-auth deployment gate is complete.

- [ ] Home compact + expanded prototype.
- [ ] Activity list -> detail/edit -> back, filters/search.
- [ ] Quick Entry prototypes: expense, transfer, card payment, split.
- [ ] Money/Cards list -> secure-detail prototype with fake vault.
- [ ] Plan list -> complex editor prototype.
- [ ] Insights chart + text/accessibility summary + drill-down.
- [ ] Adaptive list-detail representative flow.
- [ ] Large-font, TalkBack semantics and touch-target validation.
- [ ] Validate whether 5 top-level destinations remain clearer than a 4-destination alternative; record final IA ADR.

## Phase 3 — Main MyFinHub backend native-client gate

Dependency tracker: issue #4. Implementation occurred in `MariosGiannakaras/MyFinHub` through issue #196 / PR #197.

- [x] Define and implement explicit native bearer authentication contract.
- [x] Preserve existing cookie + same-origin behavior unchanged for web/desktop.
- [x] Accept valid Supabase bearer JWT only on explicitly opted-in finance/card endpoints.
- [x] Enforce owner UID + AAL2 + RLS/RPC for native access.
- [x] Preserve `If-Match` revision conflict semantics.
- [x] Preserve card-secret authorization/validation and CVV rejection.
- [x] Add negative/positive auth, CSRF-separation, revision and card-secret tests.
- [x] Document endpoint/client contract consumed by Android.
- [x] Pass exact-head CI, CodeQL, cross-engine, performance and Windows Desktop regression gates.
- [x] Squash-merge backend PR #197 to MyFinHub `develop` (`53b14e7cde63e7d84e6a552f55c709f2d746f42f`).
- [ ] Promote/deploy the backend contract through the normal MyFinHub `develop -> main` release path before Android production-data integration is enabled.

## Phase 4 — Real backend integration and feature parity

- [ ] Supabase password + TOTP/AAL2 native auth.
- [ ] Keystore-backed token persistence/session restoration/logout.
- [ ] Canonical `/api/data` load/save/revision conflict integration.
- [ ] Home parity.
- [ ] Activity/Transactions parity.
- [ ] Smart Review parity.
- [ ] Savings parity.
- [ ] Cards/Credit parity.
- [ ] Loans/installments parity.
- [ ] Lending/receivables parity.
- [ ] Recurring/scheduled parity.
- [ ] Budgets/rules/forecast/planning parity.
- [ ] Needs Attention parity.
- [ ] Reports/Insights parity.
- [ ] Settings parity.
- [ ] Import/backup flows where applicable to mobile.
- [ ] Undo/Redo and privacy-safe Change History behavior appropriate to Android session semantics.

## Phase 5 — Security/performance/release hardening

- [ ] Real server PAN/expiry vault integration.
- [ ] Device-local CVV Android Keystore vault.
- [ ] Scoped secure-window/recent-thumbnail behavior for secret reveal screens.
- [ ] No-sensitive-log/test/preview verification.
- [ ] App-specific Baseline Profile generation.
- [ ] Macrobenchmark startup/Home/Activity/Quick Entry critical journeys.
- [ ] Release R8/minification/resource shrinking checks.
- [ ] Final emulator/device-class regression matrix.

## Phase 6 — Final run/build handoff

The user will install Android Studio only when the application reaches the final run/build checkpoint. APK generation is therefore not a routine development artifact.

- [ ] Freeze release candidate on `main` after all functional/security/quality gates pass.
- [ ] Document the exact Android Studio/JDK/SDK prerequisites and one-command Gradle validation path.
- [ ] Ensure the repository opens and Gradle-syncs without secret files or local-only project surgery.
- [ ] Provide final first-run configuration instructions for the real backend/auth values that cannot be committed to the public repository.
- [ ] Perform final `test lint assembleDebug`/release-config validation in GitHub before handoff.
- [ ] Have the user perform the first real-device run only at the final checkpoint.
- [ ] Generate/sign an APK only when explicitly requested: either via Android Studio/local Gradle at handoff or through a dedicated secure release workflow added at that time.
- [ ] Preserve one long-lived signing key once release signing begins so future versions can update the installed app.

### Optional future automation, not required now

A private GitHub distribution repository, protected signing environment and tag-gated signed-APK workflow may be added later if automated APK delivery becomes useful. They are intentionally not prerequisites for ongoing application development.

## Future platform note — not a current requirement

Direct signed-APK sideloading remains the baseline. Android developer verification / limited distribution is not part of the current implementation workflow. Reassess only if future Android enforcement materially affects the personal device/use case.
