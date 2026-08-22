# MyFinHub Android future extensions

This branch is a holding area for explicitly deferred Android expansion specifications, experiments and prototypes. It is not the normal integration branch and it must not become a parallel production line.

## Rules

- Start normal product work from current `develop`, not from this branch.
- Record every proposed extension in a GitHub Issue before implementation becomes active product work.
- Keep experiments synthetic and free of real finance data, credentials, tokens, card secrets and signing material.
- Do not weaken the canonical MyFinHub backend/security contracts for an experiment.
- When an extension is approved, copy/reimplement only the validated delta on a fresh short-lived branch from current `develop`, then use the normal PR/test workflow.
- Do not merge unfinished extension code directly into `develop` or `main`.

## Holding checklist

- [ ] No future extension is currently approved for implementation from this branch.

The active Android roadmap is tracked in `develop` through `TODO.md`, `STATUS.md`, Issues and PRs.
