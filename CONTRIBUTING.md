# Contributing and repository workflow

## Branches

### `main`
Release/promotion baseline. Do not develop directly on `main`.

### `develop`
Primary integration branch. Completed implementation/research batches arrive through PRs after their tracked issue acceptance criteria pass.

### `extensions`
Long-lived holding branch for future/deferred expansion specifications, experiments and prototypes that are intentionally outside the current product delivery path. Do not merge unfinished extension code into `develop`. When an extension is approved for product work, create/update a dedicated Issue and implement it from a fresh branch based on current `develop`; promote only the validated delta through PR review. Keep `extensions` periodically synchronized enough to avoid misleading stale documentation, but do not use it as a second integration branch.

### Short-lived work branches
- `feature/<scope>`
- `fix/<scope>`
- `research/<scope>`
- `docs/<scope>`

Default base: `develop` unless the issue explicitly documents another base.

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

Keep PRs coherent and small enough to verify. Preserve unrelated changes. Squash merge is preferred for completed batches unless history structure requires otherwise.

## Progress tracking

- durable rules/architecture: `AGENTS.md`, `docs/`;
- current project state: `STATUS.md`;
- phased backlog/checklists: `TODO.md`;
- work-level progress/evidence: Issues and PRs;
- implementation history: commits.

Do not duplicate large stable instructions across every Issue.

## Validation

Use the narrowest relevant checks first. The baseline Android project gate is:

```bash
./gradlew test lint assembleDebug
```

Instrumented, screenshot, adaptive-device and performance tests are added as their corresponding implementation surfaces arrive.
