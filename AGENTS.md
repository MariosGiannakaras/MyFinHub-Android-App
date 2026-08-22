# MyFinHub Android repository instructions

## Mission

Build a native Android client for MyFinHub that preserves the product's finance semantics and security boundaries while providing a mobile-first Android experience. The app must not be a WebView, PWA shell, browser launcher, or thin wrapper around the web UI.

## Source of truth

The existing `MariosGiannakaras/MyFinHub` repository owns the canonical backend, Supabase schema/migrations, finance domain semantics, authorization model, validation rules, optimistic revision behavior, backups/audit behavior, and server-side card-secret vault. Do not duplicate or silently reinterpret these contracts in Android.

When Android work depends on a backend change, inspect the current MyFinHub implementation and documentation first. Implement compatible changes in the correct repository through its normal Issue -> branch -> PR workflow.

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
- Use short-lived branches from `develop` and PR back into `develop` for implementation/research batches.
- Production/release promotion is deliberate and traceable from `develop` to `main`.
- Keep durable decisions in `docs/`; keep changing progress in `STATUS.md`, `TODO.md`, Issues, PRs, and commits.
- Public source is intentional. Public workflows must never upload signed private APKs or secrets as public artifacts/releases.
- Release signing uses GitHub environment/repository secrets and a protected release job. Signed APKs are transferred directly to a private GitHub distribution repository; they are never staged as public-repo workflow artifacts.
- Pin third-party GitHub Actions by immutable commit SHA where practical. Prefer first-party GitHub/Gradle/Android tooling and least-privilege `GITHUB_TOKEN` permissions.

## Validation order

Run the narrowest relevant validation first, then broaden only as needed:

1. Kotlin/unit tests for changed domain/client logic.
2. Compose component/feature tests and screenshot validation.
3. Android Lint/static checks.
4. Instrumented Compose tests on build-managed devices for affected screen classes.
5. Full debug/release assembly as appropriate.
6. Release-path checks: signing certificate fingerprint, APK verification, SHA-256, install/upgrade smoke, critical user journeys, and private-distribution upload.

Performance conclusions must come from release/benchmark builds. Use Macrobenchmark/Baseline Profiles for critical journeys rather than debug timings.

## Scope discipline

Do not invent new finance semantics while adapting the UI. Preserve unrelated user changes. Avoid broad cleanup, speculative features, whole-repository rewrites, or premature abstraction. Stop when the acceptance criteria for the tracked Issue/PR pass.