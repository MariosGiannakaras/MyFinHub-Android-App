# MyFinHub Android — Design System & Pixel-Spec hardening plan

Status: active before Phase 6
Tracker: issue #47
Scope: Android only. Physical Galaxy S24 Ultra acceptance, production Auth/API validation and production signing/release remain Phase 6.

## Why this pass exists

The 2026 redesign and UI/UX hardening established the right product hierarchy and interaction behavior, but not every retained component was converted into a documented numeric design contract. This pass removes unexplained visual magic numbers, makes Material 3 inheritance explicit, and turns the visual system into something testable before real-device acceptance.

## Authoritative guidance used

- Android Core App Quality: interactive touch targets at least 48dp; text/foreground contrast 4.5:1 for normal text and 3:1 for large text/graphics.
- Jetpack Compose Material 3: semantic shape roles and theme-level shape overrides.
- Material 3 Compose Button baseline: 40dp minimum visible height, 18dp baseline icon, 8dp icon/label gap. MyFinHub intentionally raises primary finance actions to a visible 48dp minimum so the visible control matches the minimum touch target.
- Material 3 Compose text fields: 56dp minimum height; 1dp unfocused and 2dp focused border/indicator.
- Material components keep framework defaults where those defaults already satisfy product semantics and accessibility. MyFinHub owns only deliberate product-level deviations.

## Foundation contract

### Spacing

| Token | Value | Intended use |
| --- | ---: | --- |
| micro | 2dp | tightly related text/meta stacks only |
| xxs | 4dp | compact internal separation |
| xs | 8dp | icon/label gap, compact grouping |
| sm | 12dp | row/control internal grouping |
| md | 16dp | card content padding |
| lg | 20dp | compact-phone screen edge and section gap |
| xl | 24dp | large section separation |
| xxl | 32dp | major page-state separation |

### Shapes

Target semantic scale:

| Role | Radius |
| --- | ---: |
| extraSmall | 8dp |
| small | 12dp |
| medium | 16dp |
| large | 24dp |
| extraLarge | 28dp |

Usage intent: text fields/menus = extraSmall, chips = small, cards/rows = medium, sheets/FAB-like larger surfaces = large, dialogs/large transient surfaces = extraLarge unless the Material component owns a stronger default shape.

### Core geometry

| Component/role | Specification |
| --- | --- |
| Minimum interactive touch target | 48dp × 48dp |
| Compact screen horizontal edge | 20dp |
| Standard card content padding | 16dp |
| Standard card border | 1dp decorative outlineVariant |
| Finance row padding | 16dp horizontal / 12dp vertical |
| Icon badge | 40dp container / 20dp icon |
| Standard icon | 20dp |
| Compact control icon | 18dp |
| Primary finance action | >=48dp visible height, 20dp horizontal padding, 12dp vertical padding, 18dp icon, 8dp icon-label gap |
| Outlined text field | >=56dp height, 1dp unfocused border, 2dp focused border |

## Contrast contract

- Normal/small text: >=4.5:1 against its actual rendered background.
- Large text and essential graphical/control boundaries: >=3:1.
- Decorative separators may be lower contrast only when they are not required to perceive component boundaries/state.
- Color must never be the only carrier of financial meaning.

Initial audit found the old light-theme income, expense, attention and neutral semantic accents were below 4.5:1 in at least one small-text usage. The first implementation batch darkens those semantic accents while keeping their hue families and validates both surface and semantic-container pairings.

## Component matrix

### Batch 1 — Foundations + shared primitives

- [x] Create explicit geometry contract (`MyFinHubDesignMetrics`).
- [ ] Normalize spacing/shape roles in the theme.
- [ ] Fix semantic light-theme contrast failures.
- [ ] Remove shared-component ad-hoc sizes where a token now exists.
- [ ] Add unit contracts for dimensions and contrast.

### Batch 2 — Controls

- [ ] Buttons / text buttons / icon buttons / FAB.
- [ ] Text fields / selectors / date controls.
- [ ] Chips / switches / segmented choices.
- [ ] Explicit disabled/focused/error/pressed-state review.

### Batch 3 — Containers and repeated data

- [ ] Cards / action cards / finance rows / list rows / dividers.
- [ ] Headers / section headings / amount alignment.
- [ ] Dialogs / sheets / snackbars / system states.
- [ ] Navigation bar/item geometry and selected-state treatment.

### Batch 4 — Screen rhythm

- [ ] Home.
- [ ] Activity.
- [ ] Quick Entry and split editor.
- [ ] Money.
- [ ] Plan.
- [ ] Insights.
- [ ] Settings / Diagnostics / Change History.

For each screen record outer margins, vertical rhythm, section gaps, row density, title/subtitle spacing, primary-action placement and any intentional exception from the foundation contract.

### Batch 5 — Accessibility and visual states

- [ ] Light/dark contrast matrix.
- [ ] Normal and large font scale.
- [ ] Empty/loading/error/offline/conflict/pending states.
- [ ] TalkBack labels/state announcements and focus order.
- [ ] 48dp interactive-target audit for custom controls.

### Batch 6 — Screenshot acceptance

- [ ] Render real Compose candidates for every visually changed canonical surface.
- [ ] Personally inspect compact normal-font candidates.
- [ ] Personally inspect dark candidates.
- [ ] Personally inspect large-font candidates.
- [ ] Replace canonical references only after acceptance; do not accumulate alternatives.
- [ ] Final screenshot regression must pass without regeneration.

### Batch 7 — Merge readiness

- [ ] Unit/reducer/UI tests green.
- [ ] Normal Android CI green.
- [ ] Representative S24-target instrumentation green.
- [ ] Zero unresolved review threads.
- [ ] Merge to `develop` before Phase 6 physical-device acceptance.

## Phase 6 boundary

This pass may prepare the UI for the real device, but it does not claim Samsung-specific acceptance. `docs/PHASE_6_DEVICE_HANDOFF.md` and issue #14 remain authoritative for production Auth/API, actual One UI/display/font rendering, physical-device performance, release-candidate promotion and signing.
