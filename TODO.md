# MyFinHub Android TODO

## Phase 0 — Research/design foundation

- [x] Bootstrap public Android source repository.
- [x] Create `develop` and Phase 0 research branch.
- [x] Research current Android 2026 Compose/Material/adaptive/navigation/accessibility guidance.
- [x] Research finance/productivity examples for translating complex desktop/web capability to mobile.
- [x] Define mobile-first MyFinHub design contract.
- [x] Define Android architecture/security boundary.
- [x] Define GitHub Actions/signing/private-distribution strategy.
- [x] Identify unavoidable non-GitHub/device/Google verification steps in advance.
- [ ] Review/merge Phase 0 PR into `develop` after repository documentation review.

## Phase 1 — Android project bootstrap

- [ ] Create Gradle/Kotlin/Compose project with pinned JDK/AGP/Kotlin/Compose versions.
- [ ] Define application/package ID and versioning policy.
- [ ] Configure Material 3 theme, dark/light, typography, spacing and synthetic preview fixtures.
- [ ] Configure Navigation 3 and Material 3 Adaptive root scaffolding.
- [ ] Configure ViewModel/StateFlow/UDF foundations.
- [ ] Configure OkHttp/serialization network boundary with fake/synthetic implementation first.
- [ ] Configure Keystore-backed secure storage primitives with tests.
- [ ] Configure Android Lint, unit tests, Compose tests and official screenshot testing.
- [ ] Configure build-managed virtual device matrix for compact/medium/expanded checks.
- [ ] Add PR CI; no release secrets or signed APK artifacts yet.

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

Work occurs in `MariosGiannakaras/MyFinHub` through its normal Issue/branch/PR workflow.

- [ ] Define native bearer authentication contract.
- [ ] Preserve existing cookie + same-origin behavior unchanged for web/desktop.
- [ ] Accept valid Supabase bearer JWT for native client.
- [ ] Enforce owner UID + AAL2 + RLS/RPC for native access.
- [ ] Preserve `If-Match` revision conflict semantics.
- [ ] Preserve card-secret authorization/validation.
- [ ] Add tests for valid bearer, expired/invalid token, AAL1, non-owner, mutation CSRF separation, revision conflict, card-secret access.
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

- [ ] Create separate private GitHub distribution repository (one-time GitHub setup).
- [ ] Create protected `android-release` GitHub environment.
- [ ] Generate long-lived Android signing key and keep an offline encrypted recovery backup.
- [ ] Store CI signing copy/passwords in GitHub protected secrets.
- [ ] Store least-privilege private-distribution repository credential in GitHub secret.
- [ ] Record expected signing certificate SHA-256 fingerprint in public config/docs.
- [ ] Implement tag-gated `android-v*` release workflow from `main` only.
- [ ] Verify signing/package/version/checksum before upload.
- [ ] Upload signed APK + SHA-256 directly to private GitHub distribution release; never public artifacts/releases.
- [ ] Track release evidence in GitHub Issue/PR/release metadata.

## Future Android distribution requirement

- [ ] Before Android developer-verification global enforcement affects the device/use case, choose between Google's free limited-distribution path (recommended for personal use) and the advanced unregistered-app sideload flow.
- [ ] If using limited distribution, complete the one-time Android Developer Console package/device authorization outside GitHub; document completion/evidence in GitHub without committing personal identity data.