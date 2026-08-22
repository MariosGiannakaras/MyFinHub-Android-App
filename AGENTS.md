# MyFinHub Android repository instructions

## Mission

Build a native Android client for MyFinHub that preserves the product's finance semantics and security boundaries while providing a mobile-first Android experience. The app must not be a WebView, PWA shell, browser launcher, or thin wrapper around the web UI.

This repository is the primary home of the Android workstream. The Android workstream owns end-to-end Android architecture, app implementation, mobile UI/UX, authentication/session persistence, biometric/PIN unlock, API/backend integration required by Android, Android-specific security, Android tests/CI, synchronization with the shared backend, and final APK build/signing/distribution when release work is explicitly in scope.

## Source of truth

The existing `MariosGiannakaras/MyFinHub` repository owns the canonical backend, Supabase schema/migrations, finance domain semantics, authorization model, validation rules, optimistic revision behavior, backups/audit behavior, and server-side card-secret vault. Do not duplicate or silently reinterpret these contracts in Android.

Another workstream owns general web/desktop implementation. Android work must not take over that scope.

When Android work depends on a backend change, inspect the current MyFinHub implementation and documentation first. Make only the minimum Android-required change and keep it isolated from unrelated web/desktop work.

## Cross-repository boundary for `MariosGiannakaras/MyFinHub`

Any Android-required change in the main MyFinHub repository must follow all of these rules:

- Never commit Android integration changes directly to `main`, `develop`, or a branch owned by another workstream.
- Create and use an Android-owned branch with an explicit prefix such as `android/integration-*`, `android/auth-*`, `android/api-*`, or another equally clear Android-specific name.
- Do not refactor, clean up, reorganize, rename, or modernize unrelated web/desktop code, workflows, releases, documentation, architecture, or dependencies.
- Do not close, rewrite, or repurpose Issues/PRs owned by other workstreams unless it is strictly required for Android integration and the reason is documented.
- Keep every main-repo delta to the minimum necessary for the Android feature being implemented.
- Commits, Issues, PR titles/bodies, and relevant documentation must identify the change as originating from the Android workstream.
- Any cross-repo PR must target the repository's normal integration path but must remain an isolated Android-owned PR until the owning web/desktop workstream decides how/when to integrate it.

Every Android-originated main-repo change must explicitly record:

1. **Why required** — why Android cannot implement the feature correctly without this main-repo change.
2. **Exact change** — the minimal backend/shared contract delta.
3. **Android feature served** — the concrete Android screen/flow/capability that depends on it.
4. **Web/desktop impact** — whether behavior changes for web/desktop; expected default is no behavior change unless unavoidable and documented.
5. **Handoff note** — what the web/desktop workstream must know before integration/promotion.

If an Android requirement can be satisfied entirely inside `MyFinHub-Android-App`, do not modify `MyFinHub`.

## Android baseline

- Kotlin and Jetpack Compose.
- Material 3 and Material 3 Adaptive.
- Compose-first, single-activity navigation unless a documented Android platform requirement justifies otherwise.
- Navigation must support Android predictive back and preserve top-level destination state.
- Layout decisions use window size/posture, not hard-coded phone/tablet device labels.
- Do not lock orientation or assume one aspect ratio.
- UI state follows unidirectional data flow with lifecycle-aware ViewModels/StateFlow.
- Prefer platform/Jetpack components over custom substitutes when they provide equivalent behavior and accessibility semantics.

## Mobile design rules

- Do not port desktop layout geometry. Port user goals, information hierarchy, finance semantics, and workflows.
- Compact layouts use progressive disclosure, list/detail navigation, bottom sheets, dedicated edit screens, and contextual actions instead of desktop tables, multi-column forms, hover affordances, or dense toolbars.
- Primary compact navigation must remain within Material guidance (normally 3-5 stable top-level destinations).
- One primary quick action may use a FAB when contextually justified; avoid competing global FABs.
- Never make a destructive or essential operation gesture-only. Gestures may accelerate an action but must have a visible/accessibility equivalent.
- Interactive touch targets are at least 48dp.
- Support system font scaling, TalkBack semantics/traversal, sufficient contrast, reduced motion where applicable, keyboard/focus behavior, and non-touch input on larger devices.
- Sensitive values must not be exposed through screenshots, logs, accessibility labels, analytics, test fixtures, or preview/golden data.

## UI review/output rule

- Internal bootstrap shells, test harnesses, placeholder screens, infrastructure renders, and synthetic proof-of-render screenshots are validation evidence, not user-facing UI review material.
- Show the user screenshots only after a real application screen or coherent user flow has been implemented to a reviewable state.
- When a real UI checkpoint is ready, publish/render the actual Compose UI and present those images for review before treating the visual direction as settled.
- Screenshot tests may still use synthetic data, but the layout/component hierarchy must be the real application UI rather than a temporary bootstrap placeholder.

## Security invariants

- No service-role/secret Supabase key in the APK.
- No `CARD_VAULT_KEY` in the APK or repository.
- Finance mutations continue through the canonical MyFinHub API/security boundary unless an explicit architecture decision replaces that boundary with equivalent or stronger enforcement.
- Native bearer-session support must preserve valid Supabase session + configured owner UID + AAL2 + RLS/RPC + revision/validation requirements.
- Web/desktop cookie and same-origin protection must not be weakened to support Android.
- Durable FinanceData remains server-side by default; do not introduce a second canonical Room/SQLite finance database.
- PAN/expiry remain in the server vault. CVV remains device-local only and uses Android Keystore-backed encryption.
- Never commit real finance data, credentials, JWTs, refresh tokens, PAN/expiry/CVV, vault keys, signing keystores/passwords, or private APKs.

## GitHub workflow

- Work is tracked by Issues.
- Use short-lived branches from `develop` and PR back into `develop` for Android-repository implementation/research batches.
- Production/release promotion is deliberate and traceable from `develop` to `main`.
- Keep durable decisions in `docs/`; keep changing progress in `STATUS.md`, `TODO.md`, Issues, PRs, and commits.
- Public source is intentional. Public workflows must never upload signed private APKs or secrets as public artifacts/releases.
- Routine development does not need signed APK production. Final APK/build/signing/distribution work happens only at an explicit release checkpoint.
- Pin third-party GitHub Actions by immutable commit SHA where practical. Prefer first-party GitHub/Gradle/Android tooling and least-privilege `GITHUB_TOKEN` permissions.

## Validation order

Run the narrowest relevant validation first, then broaden only as needed:

1. Kotlin/unit tests for changed domain/client logic.
2. Compose component/feature tests and screenshot validation.
3. Android Lint/static checks.
4. Instrumented Compose tests on build-managed devices for affected screen classes.
5. Full debug/release assembly as appropriate.
6. Release-path checks when explicitly requested: signing certificate fingerprint, APK verification, SHA-256, install/upgrade smoke, critical user journeys, and distribution.

Performance conclusions must come from release/benchmark builds. Use Macrobenchmark/Baseline Profiles for critical journeys rather than debug timings.

## Scope discipline

Do not invent new finance semantics while adapting the UI. Preserve unrelated user changes. Avoid broad cleanup, speculative features, whole-repository rewrites, or premature abstraction. Stop when the acceptance criteria for the tracked Issue/PR pass.