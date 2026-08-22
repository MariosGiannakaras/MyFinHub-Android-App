# MyFinHub Android TODO

## Phase 0 — Research/design foundation

- [x] Bootstrap public Android source repository.
- [x] Create `develop` and Phase 0 research branch.
- [x] Research current Android 2026 Compose/Material/adaptive/navigation/accessibility guidance.
- [x] Research finance/productivity examples for translating complex desktop/web capability to mobile.
- [x] Define mobile-first MyFinHub design contract.
- [x] Define Android architecture/security boundary.
- [x] Define GitHub Actions/signing/private-distribution strategy.
- [x] Identify unavoidable non-GitHub/device steps in advance.
- [x] Review and squash-merge Phase 0 PR #2 into `develop`.

## Phase 1 — Android project bootstrap

Tracker: issue #3. Active implementation PR: #5.

- [x] Create initial Gradle/Kotlin/Compose project with pinned AGP/Kotlin/Compose/JDK/SDK versions.
- [x] Define application/package ID `app.myfinhub.android` and initial `0.1.0` / versionCode 1 policy baseline.
- [x] Configure Material 3 light/dark theme and synthetic bootstrap UI.
- [x] Configure Material 3 Adaptive `NavigationSuiteScaffold` root shell for bottom-bar/rail adaptation.
- [ ] Integrate Navigation 3 back stacks after the basic build gate is green.
- [x] Configure ViewModel/StateFlow/UDF bootstrap foundation.
- [x] Configure typed API boundary with fake/synthetic implementation first.
- [x] Configure Android Keystore AES-GCM primitive and an instrumented round-trip test source; no real secret storage yet.
- [x] Add unit test and initial Compose instrumentation test source.
- [ ] Configure official screenshot testing and build-managed compact/medium/expanded device matrix.
- [x] Add public-repo-safe PR CI definition; no release secrets or signed APK artifacts.
- [x] Correct CI SDK setup to API 37 + AGP 9.3 default Build Tools 36.0.0 (PR #7).
- [ ] Generate/commit Gradle wrapper and confirm `test lint assembleDebug` passes.
- [x] Add repository README, contribution/branch rules, Issue templates and PR template.
- [x] Create long-lived `extensions` branch and branch-local `EXTENSIONS.md` for explicitly deferred future expansion work.

## Phase 2 — Representative mobile prototypes

Use synthetic finance data only until the backend native-auth gate is complete.

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

Dependency tracker: issue #4. Work occurs in `MariosGiannakaras/MyFinHub` through its normal Issue/branch/PR workflow.

- [ ] Define native bearer authentication contract.
- [ ] Preserve existing cookie + same-origin behavior unchanged for web/desktop.
- [ ] Accept valid Supabase bearer JWT for native client.
- [ ] Enforce owner UID + AAL2 + RLS/RPC for native access.
- [ ] Preserve `If-Match` revision conflict semantics.
- [ ] Preserve card-secret authorization/validation.
- [ ] Add negative/positive auth, CSRF-separation, revision and card-secret tests.
- [ ] Document endpoint/client contract consumed by Android.

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
- [ ] Install/upgrade smoke from previous signed version.

## Phase 6 — GitHub private APK delivery

- [ ] Create separate private GitHub distribution repository when release delivery is ready.
- [ ] Create protected `android-release` GitHub environment.
- [ ] Generate long-lived Android signing key and keep a recoverable encrypted backup.
- [ ] Store CI signing copy/passwords in protected GitHub secrets.
- [ ] Store least-privilege private-distribution repository credential in GitHub secret.
- [ ] Record expected signing certificate SHA-256 fingerprint in public config/docs.
- [ ] Implement tag-gated `android-v*` release workflow from `main` only.
- [ ] Verify signing/package/version/checksum before upload.
- [ ] Upload signed APK + SHA-256 directly to private GitHub distribution release; never public artifacts/releases.
- [ ] Track release evidence in GitHub Issue/PR/release metadata.

## Future platform note — not a current requirement

Direct signed-APK sideloading is the baseline. Android developer verification / limited distribution is not part of the current implementation workflow. Reassess only if future Android enforcement materially affects the personal device/use case.
