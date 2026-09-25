# Android UI/UX canonical correction pass

This workstream is the retained pre-production correction pass driven by the physical Galaxy S24 Ultra review after Phase 6 build 6010.

## Scope

- Respect Android system bars; application content must not render underneath the status bar.
- Persist amount visibility/display controls through Settings instead of volatile Home-only state.
- Order activity deterministically by canonical date plus event creation/update chronology and stable fallback; newest activity stays first.
- Group activity by calendar day/month and expose contextual long-press actions.
- Expand transaction editing to canonical fields supported by the shared finance document, including category/subcategory/date/account semantics where safe.
- Use a calendar picker for editable dates instead of raw date-only text entry.
- Align Quick Entry with central `develop`: canonical category trees/subcategories/accounts, richer entry intents where Android mutation support is safe, and return to the originating screen after a successful local enqueue.
- Make pending/sync presentation non-blocking and compact while preserving NEVER_SENT Undo and NEEDS_REVIEW reconciliation rules.
- Consolidate user notices into one short heads-up presentation with durable non-sensitive details available from an in-app notification drawer; do not duplicate Snackbar + heads-up surfaces.
- Add useful account drill-down with account-filtered activity.
- Add card creation only through the canonical `state.cards` model, matching central card fields and without putting PAN/CVV/expiry into the finance document.
- Prefer canonical/server-provided finance data for categories, subcategories, accounts and card banks; icons remain Android-local presentation assets.

## Boundaries

- Android `develop` only through this isolated branch/PR.
- No Android production signing, production APK, version freeze or `develop -> main` promotion.
- Central repository is read-only for schema/behavior comparison unless a true Android backend dependency is discovered.
- Never expose PIN, PAN, expiry, CVV, TOTP, access tokens or raw finance payloads in UI evidence/diagnostics.
- Preserve offline-first causal ordering, encrypted pending queue, no-blind-retry and owner+AAL2 boundaries.

## Acceptance

Retained implementation must have unit/UI coverage, lint/build verification, real rendered light/dark/large-font screenshots personally inspected, and one minimal final physical S24 Ultra delta check after the hosted state is stable.
