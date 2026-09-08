# RC7 S24 Owner UI/UX Correction Plan

## Purpose

This document is the durable, memory-independent implementation plan for the owner-requested UI/UX correction pass that follows the physical Samsung Galaxy S24 Ultra review of production-signed `1.0.0-rc7` / versionCode `10006`.

A new chat/agent must not re-discover or restart this work. It must continue from the live GitHub checkpoint and this plan.

## Mandatory startup for any new chat/agent

1. Read root `AGENTS.md`.
2. Read permanent issue #27.
3. Read `tracking/android-project-state.json`, generated `docs/CURRENT_HANDOFF.md`, `STATUS.md`, and `TODO.md`.
4. Read this file in full.
5. Inspect live `develop`, open PRs, issue #73, recent merged PRs, and relevant checks/workflows. Live GitHub state wins if newer than tracking.
6. Resume the existing implementation branch/PR when present. Do not create duplicate issues, branches, or PRs.
7. For every materially changed UI surface: render the real Compose screen, personally inspect it, fix visual defects, run relevant UI/instrumentation gates, and replace stale screenshot evidence only after validation.
8. Overall progress remains `4/6` until the owner physically accepts the corrected higher production candidate on the Samsung Galaxy S24 Ultra.

## Confirmed physical checkpoint

- `1.0.0-rc7` / `10006` was installed **in place** over rc6 on the authorized Galaxy S24 Ultra without uninstall or data clearing.
- The app reports `1.0.0-rc7` in Settings.
- Same-signer update continuity passed.
- Existing session continuity passed after fully closing/reopening the app.
- The owner then completed a screen-by-screen physical UI/UX review and explicitly requested another correction implementation pass before final acceptance.
- Therefore rc7 is a valid production-signed technical baseline, but **not owner-accepted as visual/product final**.
- Do not promote `develop -> main` and do not claim final completion until the correction pass is physically accepted.

## Product direction for the correction pass

The owner wants each surface assessed and implemented for all of the following together:

- visual quality and modern/premium appearance,
- information hierarchy and density,
- functional usefulness, not decorative UI,
- consistent bank/provider/category identity,
- clear mobile interaction patterns and discoverability,
- correct end-to-end flow between surfaces,
- human product language instead of internal/backend terminology,
- trustworthy finance analytics and calculations,
- accessibility, dark mode, large font and S24 ergonomics,
- preservation of canonical finance/auth/offline/security semantics.

Cross-screen visual direction:

- Reduce excessive outlined rounded-card chrome and nested `card soup`.
- Prefer hierarchy from spacing, typography, surface contrast and deliberate grouping.
- Increase useful information density where list scanning is the primary task.
- Use consistent provider/logo containers and real provider marks where supported.
- Replace large anchored `DropdownMenu` pickers on compact mobile screens with searchable/structured Material bottom sheets where appropriate.
- Keep destructive operations explicit; never gesture-only.
- Use Greek, human-readable dates and user-facing copy. ISO timestamps/canonical/debug codes may remain internal or secondary diagnostic details.
- Internal words such as `canonical`, raw API/auth error codes, implementation terminology and backend constraints must not be primary consumer copy.

## Ordered implementation slices

Implementation should proceed in these slices. Complete, screenshot-validate and host-validate a slice before moving to the next unless a shared primitive deliberately spans adjacent slices.

### Slice A — Shared mobile interaction foundations + Quick Entry

Priority: highest daily-use correction.

Required outcomes:

- Replace account/category/advanced-type compact `DropdownMenu` interactions with mobile bottom-sheet selection patterns.
- Account picker: provider/account identity, clear selected state, safe source/destination filtering.
- Category picker: search or structured grouping; prioritize recent/common choices before the complete list when data supports it.
- Advanced movement types: bottom sheet with clear labels and descriptions rather than a long floating menu.
- Keep contextual account labels: expense `Από λογαριασμό`, income `Σε λογαριασμό`, transfer `Από -> Προς`.
- Transfer destination must never allow the selected source account.
- Reduce the oversized amount hero so amount/type/account/category/save fit with substantially less scrolling on S24.
- Fix quick-entry helper copy so it describes fields that actually exist; do not claim a required `title` when the UI does not expose one.
- Date UI must be human-readable Greek to the user; keep canonical ISO representation internally as needed.
- Date picker must not expose English `Select date`, `September`, etc. in the Greek product experience.
- Offline success should read as successful local save with pending sync, not as a warning: e.g. `Αποθηκεύτηκε` + `Θα συγχρονιστεί όταν υπάρχει σύνδεση` + `Αναίρεση`.
- Preserve the encrypted local enqueue/reconcile/undo contract exactly.

### Slice B — Home

Required outcomes:

- Establish a clearer top-level summary before the account list; show the most decision-useful snapshot while respecting hide-amounts privacy.
- Make `Νέα κίνηση` a clear primary action rather than an incidental text link.
- Reorder attention items above secondary accounts when action is required.
- Make alert CTAs specific (`Προβολή`, `Τακτοποίηση`, etc.) rather than generic `Έλεγχος` when semantics allow.
- Improve main account cards: less unused height, better hierarchy, consistent provider/logo identity.
- Improve 7-day trend sparklines so they look intentional and communicate direction/delta rather than prototype lines.
- Use real provider marks for supported institutions and a consistent fallback container for cash/unknown providers.
- Ensure the home flow reads: summary -> primary action -> attention -> main accounts -> secondary accounts.

### Slice C — Activity / Transactions

Required outcomes:

- Reduce search/filter vertical overhead and transaction-row height to improve ledger scanning density.
- Replace account filter popup with a mobile bottom sheet with provider identity and selected state.
- Add compact useful filters for date/type/category/amount only where supported by existing semantics.
- Keep date grouping but tighten spacing and preserve clear month/day hierarchy.
- Distinguish transfers from income/expense visually and semantically (`Από -> Προς`, neutral treatment).
- Preserve `+` / `-` or equivalent semantic sign in addition to color.
- Ensure the FAB never obscures the final rows.
- If a monthly summary is shown, it must be derived from canonical data and not duplicate Insights incorrectly.

### Slice D — Money / Net Worth / Cards / details

Required outcomes:

- Keep net-worth arithmetic trustworthy and explain asset/liability/claim composition more clearly.
- Make it explicit that `Αποταμίευση` is a subset/summary when it reflects an account already counted above; avoid apparent double counting.
- Remove excessive vertical dead space around cards/pager content.
- Keep provider marks consistent across account rows.
- Replace destructive swipe-only card deletion with an explicit accessible destructive action and confirmation; gesture may remain only as an accelerator if a visible equivalent exists.
- Card reveal/copy/delete controls need clear semantics and minimum touch targets.
- Cash account detail should use denser ledger rows and clear transfer treatment.
- Savings/Loans/Claims details must not expose internal phrases like `canonical δυνατότητα`; use concise consumer empty states and show source/last-sync information only when useful.
- Detail navigation must have a clear hierarchy; avoid confusing simultaneous drill-down and top-level navigation behavior.

### Slice E — Plan / cash-flow / budget

Required outcomes:

- Make forecast explainable: current position -> obligations -> expected income -> transfers -> projected position, with a clear horizon/date.
- Introduce a compact cash-flow timeline/visual only if it materially helps identify tight dates.
- Group obligations by urgency (`Καθυστερημένα`, `Αυτή την εβδομάδα`, `Αργότερα`).
- Investigate apparent duplicate rent entries. If same canonical event appears twice, fix the duplication source/presentation; if distinct, explain the distinction in UI.
- Normalize all user dates to Greek human-readable formatting; do not mix `2 Σεπ` with `2025-09-04`.
- Use category/service identity rather than the same calendar icon everywhere when the data supports it.
- Make expected income and scheduled transfers visibly reconcile with the forecast.
- Upgrade monthly budget from `limit + alert` to a useful progress view: spent, remaining, percentage and warning threshold.
- Remove mixed-language/internal copy such as `Alert 80%` and `canonical αποθήκευση`.

### Slice F — Insights (`Εικόνα` / possible rename)

Required outcomes:

- Correct period comparability. Never present partial September vs full August as a direct percentage comparison without an explicit equivalent-period basis.
- Prefer `1-8 Σεπ vs 1-8 Αυγ`, clear `Μέχρι σήμερα`, or an explicitly labeled full-month projection.
- Replace ambiguous metrics such as `Αποταμίευση -329%` and `Διαφορά ροής` with clearly defined finance metrics.
- Keep the multi-month income/expense chart, but mark partial months and support exact values/accessibility.
- Keep horizontal category bars rather than replacing them with a pie chart; add percentage context and useful drill-down.
- `Δες τις κινήσεις` must deep-link to the relevant period/category filter when possible.
- Evaluate renaming top-level `Εικόνα` to a clearer Greek analytics label (candidate: `Ανάλυση`) only after checking navigation width, existing tests and owner-visible consistency.

### Slice G — Settings / diagnostics / notifications

Required outcomes:

- Simplify Settings visual chrome and grouping; keep appearance/privacy/update/account actions clear.
- Keep technical diagnostics available but secondary/expandable and support-focused.
- Human-readable state first; raw diagnostics second.
- Convert raw codes (`MFH-AUTH-NETWORK`, `MFH-API-AUTH_REQUIRED-401`, etc.) to a user-facing description with the technical code as secondary/copyable detail.
- Convert ISO timestamps to human-readable local dates/times in the UI while preserving raw value internally if needed.
- Avoid primary consumer strings such as `Production public client` or `AAL2`; they may remain in secondary diagnostics for support.
- Notification history must remain privacy-safe and must never include finance payloads, PAN/CVV/PIN/TOTP/tokens or other secrets.

### Slice H — Full validation and corrected production candidate

Required outcomes before a higher candidate is requested:

- Fresh actual Compose screenshots for every materially changed production surface/state.
- Personal visual inspection against the S24 owner findings in this document.
- Light/dark and large-font checks for affected screens.
- TalkBack/semantics/touch-target regressions checked where interaction components changed.
- Relevant unit/Compose tests, Android Lint/static checks, screenshot regression and S24-target instrumentation green.
- Production auth/API and offline/reconcile semantics regression-protected.
- Only after exact-head hosted validation: request the next protected same-signer candidate with a strictly higher versionCode than `10006`.
- Install that candidate in place on the authorized S24 without clearing data and repeat physical owner acceptance.
- Only explicit owner acceptance permits stable-final tracking and deliberate `develop -> main` release promotion.

## Current implementation checkpoint

- Active issue: #73.
- Active correction branch: `android/rc7-owner-ui-ux-correction-pass`.
- This pass begins with Slice A (shared mobile pickers + Quick Entry).
- If an open PR exists for this branch, resume it; do not create another one.
- After each merged slice, update `tracking/android-project-state.json` in the same PR so the generated handoff points to the exact next slice/checkpoint.

## Definition of done for each UI slice

A slice is not done merely because code compiles. It is done only when:

1. The intended user flow works with real production Compose hierarchy.
2. Changed semantics preserve canonical finance/auth/offline behavior.
3. Fresh rendered screenshots are visually inspected and obvious defects corrected.
4. Accessibility/touch-target/large-font implications have been considered and tested where relevant.
5. Relevant hosted checks are green.
6. Canonical project tracking states exactly what was completed and what starts next.

## Owner update format

Every substantive project update begins exactly with:

`Συνολικά: 4/6`

Then report only the current slice/component and remaining work. Do not include commit SHAs or workflow IDs unless the owner asks.
