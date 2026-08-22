# MyFinHub Android

Native Android client for MyFinHub.

## Product contract

- Native Android application; not a WebView, PWA shell, or browser launcher.
- Kotlin + Jetpack Compose is the baseline Android stack.
- The Android client uses the same canonical MyFinHub backend and Supabase/PostgreSQL source of truth as web and Windows.
- Finance semantics, validation, owner authorization, AAL2 MFA, RLS/RPC checks, optimistic revisions, backups, audit behavior, and card-vault boundaries must remain compatible with the main MyFinHub application.
- The UI is designed mobile-first for Android rather than copied or shrunk from the desktop/web interface.

## Repository workflow

Research, design decisions, implementation, tests, packaging, and CI/CD are tracked in GitHub. The public source repository is intentional for GitHub Actions economics; secrets and private signed APK distribution must remain outside public repository contents and public release assets.

Phase 0 is tracked in issue #1. Feature implementation must follow the research/design contracts under `docs/` once they are merged.

## Security

Never commit real finance data, credentials, access/refresh tokens, card secrets, signing keystores/passwords, service-role credentials, or private release APKs.
