# MyFinHub Android — UI/UX hardening plan

Status: active pre-Phase-6 hardening pass
Scope: Android app only. Production signing/release and physical Galaxy S24 Ultra acceptance remain Phase 6.

## Goal

Make the app behave like a trustworthy native finance product rather than a collection of individually polished screens. The pass prioritizes interaction truthfulness, information hierarchy, accessibility, recoverability, mobile input ergonomics, and consistency before additional visual decoration.

## Principles

1. A control that looks functional must complete a real flow or clearly state that it is unavailable/read-only.
2. A Save/Update action must mean durable persistence; local preview state must never masquerade as server state.
3. The primary financial question or action on each screen must dominate the hierarchy.
4. Repeated daily actions should take the fewest practical taps and the least typing.
5. Errors belong next to the affected field/action and must remain understandable with TalkBack.
6. Color is supplemental meaning, never the only carrier of meaning.
7. Touch targets, spoken labels, focus order, font scaling, and contrast are release criteria.
8. Loading/offline/conflict/pending/empty states are first-class states, not afterthoughts.
9. Prefer native Material 3 patterns over custom controls unless product semantics require otherwise.
10. Screenshots validate rendered output; code review alone is not visual acceptance.

## Workstream A — Navigation and task completion

- [ ] Connect Home quick-entry selection to the real canonical Quick Entry screen.
- [ ] Preselect the chosen transaction type when entering from Home.
- [ ] Remove duplicate compact Home quick-entry promotion once the primary FAB covers the task.
- [ ] Keep an accessible non-FAB entry point where the layout genuinely needs one.
- [ ] Move logout out of the persistent global overlay and into Settings/account actions.
- [ ] Verify Android back behavior from every nested editor/detail screen.
- [ ] Verify destination state restoration when switching top-level tabs.
- [ ] Audit every CTA for dead ends, no-op actions, duplicate routes, and hidden state changes.
- [ ] Audit destructive actions for clear consequence copy and safe confirmation.
- [ ] Verify predictive-back compatibility at the final platform pass.

## Workstream B — Persistence truthfulness and state ownership

- [ ] Inventory every control that mutates only frontend/local state.
- [ ] Connect persistent product edits to canonical mutations where backend semantics exist.
- [ ] Otherwise present local-only features explicitly as local preferences or read-only previews.
- [ ] Remove synthetic production-looking Change History records.
- [ ] Never label a local draft as “saved” without qualification.
- [ ] Make pending/saving/synced/conflicted states visually and semantically distinct.
- [ ] Disable duplicate write actions while a mutation is in flight.
- [ ] Preserve user-entered drafts on recoverable failure.
- [ ] Keep retry semantics aligned with issue #27; never introduce blind write retry.
- [ ] Verify offline-before-write and reconnect behavior does not create duplicate mutations.

## Workstream C — Forms and mobile input ergonomics

- [ ] Use decimal keyboards for money fields.
- [ ] Use appropriate keyboard/capitalization behavior for person, note, labels, and search fields.
- [ ] Add useful IME Next/Done actions and focus progression.
- [ ] Replace raw date-format memorization with native Material 3 date selection where practical.
- [ ] Keep a keyboard-accessible/manual date fallback if required.
- [ ] Show field-specific errors using `isError`, supporting text, and error semantics.
- [ ] Scroll/focus the first invalid field after Save.
- [ ] Preserve user input when changing unrelated selectors.
- [ ] Confirm number parsing for Greek decimal conventions.
- [ ] Prevent impossible account/card/category combinations before Save where possible.
- [ ] Improve split-entry editing for repeated parts and long labels.
- [ ] Make optional vs required fields obvious without relying on placeholder text.

## Workstream D — Feedback, errors, offline and recovery

- [ ] Replace generic/global validation messages with contextual feedback where possible.
- [ ] Ensure every long-running save has visible progress without blocking unrelated reading.
- [ ] Use action-specific retry labels instead of generic technical wording.
- [ ] Distinguish offline waiting, revision conflict, authentication expiry, and server failure.
- [ ] Make destructive discard wording describe exactly what will be lost.
- [ ] Ensure Snackbar placement never collides with navigation/FAB/system UI.
- [ ] Keep diagnostic details optional and privacy-safe.
- [ ] Add explicit empty states with the next useful action.
- [ ] Add stale-data/reconnect wording where the displayed data may no longer be current.
- [ ] Avoid modal dialogs for routine information; reserve them for decisions/blocking recovery.

## Workstream E — Accessibility and inclusive interaction

- [ ] Add automated accessibility checks for touch targets, contrast, traversal, and labels.
- [ ] Keep every interactive target at least 48×48 dp unless the Material component guarantees it.
- [ ] Review TalkBack output for Home, Activity, Quick Entry, Money, Plan, Insights, Settings, auth, and card-secret flows.
- [ ] Remove duplicate spoken content caused by icon + parent semantics.
- [ ] Verify headings form a meaningful navigation structure.
- [ ] Verify stateful controls announce selected/checked/error states.
- [ ] Verify finance colors are accompanied by text/icon semantics.
- [ ] Test 1.3× and larger font scaling for clipping, truncation, and navigation labels.
- [ ] Test switch/keyboard focus order on critical forms.
- [ ] Verify content remains usable with amounts hidden.

## Workstream F — Information architecture and visual hierarchy

- [ ] Reduce compact Home to: financial position → attention → upcoming → monthly flow.
- [ ] Remove low-frequency Settings/History controls from the main financial content stream.
- [ ] Reduce “cardification”; use cards only for genuinely separate modules.
- [ ] Prefer list rows/dividers for repeated homogeneous financial records.
- [ ] Keep one visually dominant primary action per screen.
- [ ] Standardize section title/subtitle spacing and density.
- [ ] Standardize amount alignment and tabular readability where numeric comparison matters.
- [ ] Review use of uppercase labels and secondary copy for scanability.
- [ ] Ensure important debt/due-date information is not visually subordinate to decorative metadata.
- [ ] Keep finance semantic colors consistent in light/dark themes.
- [ ] Review dark-mode contrast for muted text, borders, progress tracks, chips, and disabled states.

## Workstream G — Component-system cleanup

- [ ] Add shared money input configuration rather than repeating ad-hoc text fields.
- [ ] Add shared field-error/support patterns.
- [ ] Add shared empty-state and inline-status patterns.
- [ ] Add shared destructive/account action styling.
- [ ] Audit icons for consistent size, weight, semantics, and decorative `contentDescription = null` usage.
- [ ] Audit corner radii/elevation/borders to avoid every surface looking identical.
- [ ] Keep custom components aligned with Material interaction states (pressed, focused, disabled).
- [ ] Avoid visual tokens that are unused or screen-specific without product meaning.

## Workstream H — Perceived performance and motion

- [ ] Avoid replacing an otherwise usable screen with full-screen loading for short mutations.
- [ ] Preserve stable content while refreshing when safe.
- [ ] Check expensive lists/composables for unnecessary recomposition.
- [ ] Use motion only to explain navigation/state change, not as decoration.
- [ ] Respect reduced-motion/system expectations where applicable.
- [ ] Validate startup and first-content experience on the physical S24 Ultra during Phase 6.

## Workstream I — Validation and screenshot acceptance

- [ ] Update unit/reducer tests for changed interaction contracts.
- [ ] Update navigation/instrumentation tests for repaired flows.
- [ ] Add accessibility regression tests for the new form/error patterns.
- [ ] Run screenshot regression after every visual batch.
- [ ] Personally inspect compact canonical screenshots at normal font scale.
- [ ] Personally inspect large-font canonical screenshots.
- [ ] Replace old canonical screenshots; do not accumulate alternate “approved” versions.
- [ ] Run normal Android CI/lint/test/instrumentation gates before merge.
- [ ] Leave Samsung-specific rendering/performance/production Auth/API acceptance to Phase 6.

## Execution order

### Batch 1 — Interaction truthfulness
Home → real Quick Entry, logout placement, removal/relabeling of prototype-looking local/synthetic surfaces.

### Batch 2 — Transaction-entry ergonomics
Money keyboards, date handling, field-level validation, focus progression, split-editor density, dirty-draft behavior.

### Batch 3 — Persistence and recovery UX
Audit Money/Plan/Settings mutations, make state ownership explicit, improve pending/offline/conflict feedback.

### Batch 4 — Information hierarchy
Home simplification, repeated-row presentation, card reduction, action priority, numerical scanability.

### Batch 5 — Accessibility
Automated Compose checks plus semantics/focus/font-scale corrections across all critical surfaces.

### Batch 6 — Visual/system polish
Dark mode, spacing/token cleanup, states, motion/perceived performance, final canonical screenshot replacement.

### Batch 7 — Merge readiness
Full automated gates, screenshot inspection, tracker/status synchronization. Physical Galaxy S24 Ultra acceptance and signing remain Phase 6.
