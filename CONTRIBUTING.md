# Contributing and repository workflow

## Branches

### `main`
Release/promotion baseline. Do not develop directly on `main`.

### `develop`
Primary integration branch. Completed Android implementation/research batches arrive through PRs after their tracked issue acceptance criteria pass.

### `extensions`
Long-lived holding branch for future/deferred expansion specifications, experiments and prototypes that are intentionally outside the current product delivery path. Do not merge unfinished extension code into `develop`. When an extension is approved for product work, create/update a dedicated Issue and implement it from a fresh branch based on current `develop`; promote only the validated delta through PR review. Keep `extensions` periodically synchronized enough to avoid misleading stale documentation, but do not use it as a second integration branch.

### Short-lived Android-repository work branches
- `android/<coherent-scope>` (current Android convention)
- `feature/<scope>`, `fix/<scope>`, `research/<scope>`, `docs/<scope>` (also accepted)

Default base: `develop` unless the issue explicitly documents another base.

## Main MyFinHub cross-repository rule

The general web/desktop MyFinHub workstream is owned elsewhere. Android work must not modify unrelated web/desktop implementation.

If Android requires a change in `MariosGiannakaras/MyFinHub`:

- create an Android-owned branch such as `android/integration-*`, `android/auth-*`, or `android/api-*`;
- never develop that Android delta directly on `main`, `develop`, or another workstream's branch;
- make only the smallest change required by the Android feature;
- do not perform unrelated refactors, cleanup, dependency upgrades, workflow changes, release changes, or documentation rewrites;
- keep the Android-originated PR isolated for review/integration by the owning MyFinHub workstream.

Every such Issue/PR must document:

1. why the main-repo change is required;
2. exactly what changes;
3. which Android feature depends on it;
4. whether web/desktop behavior is affected;
5. what the other MyFinHub workstream must know before integration.

If the feature can be implemented entirely in this Android repository, do not touch the main MyFinHub repository.

## Issue discipline

Every non-trivial work batch has an Issue containing:

- goal and scope;
- exclusions;
- security/data constraints;
- implementation checklist with Markdown checkboxes;
- acceptance criteria;
- validation requirements;
- dependencies/blockers.

Update issue checkboxes when work actually completes. Do not mark future intent as completed evidence.

## Pull requests

PRs normally target `develop`, link the owning Issue and contain:

- outcome;
- changed surfaces/files;
- security/privacy impact;
- tests/validation performed;
- remaining blockers or intentionally deferred work.

Keep PRs coherent and reviewable, but group related Android journey fixes into substantial batches rather than opening a separate PR for each guard, assertion or documentation update. Preserve unrelated changes. Squash merge is preferred for completed batches unless history structure requires otherwise.

## UI review discipline

- Do not present bootstrap shells, placeholders, test harnesses, or proof-of-render screenshots as product UI.
- User-visible screenshot review begins only when a real Android application screen or coherent flow is implemented.
- Review images must render the actual production-intended Compose hierarchy, using synthetic data when required for privacy/testing.
- Screenshot artifacts may be generated continuously for CI, but only meaningful application UI checkpoints are surfaced for product review.

## Progress tracking and agent handoff

- Durable rules and architecture: root `AGENTS.md` and stable specifications in `docs/`.
- **Only hand-edited current status/backlog:** `tracking/android-project-state.json`.
- Derived views: `STATUS.md`, `TODO.md` and `docs/CURRENT_HANDOFF.md`, generated together by `python3 scripts/render_project_tracking.py`; do not manually edit them independently.
- Work-level evidence: live Issues, PRs, exact-head CI and commits. Issue #27 is a permanent **index**, not a second mutable status document.
- A new chat begins with the short AGENTS startup contract, current canonical state and the active live PR; inspect other documents only as needed. Historical issue bodies and dated plans never override the canonical state.
- Track coherent checkpoints with verified commits; do not run a full CI cycle per tiny edit. Separate final owner physical acceptance from hosted checks.

Do not duplicate large stable instructions across every Issue or chat.

## Validation

Use the narrowest relevant checks first. The baseline Android project gate is:

```bash
./gradlew test lint assembleDebug
```

Instrumented and screenshot validation run when affected paths require them; device-specific acceptance remains tied to the supported Galaxy S24 Ultra. Routine development does not publish signed artifacts. Production signing/publication happens only through the protected release workflow at an explicit release checkpoint.