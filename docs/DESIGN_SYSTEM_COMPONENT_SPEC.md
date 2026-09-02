# MyFinHub Android — Component pixel specification

Status: active implementation contract for issue #47

This document distinguishes **MyFinHub-owned values** from **intentional Material 3 inheritance**. A component should not copy framework token values into local screen code unless MyFinHub deliberately overrides them.

## Global foundations

### Spacing and rhythm

- micro: 2dp — tightly related text/meta only.
- xxs: 4dp — very compact internal separation.
- xs: 8dp — icon/label and compact control gaps.
- sm: 12dp — row/control grouping.
- md: 16dp — standard card content padding.
- lg: 20dp — compact-phone screen edge and standard section gap.
- xl: 24dp — large separation.
- xxl: 32dp — major state/page separation.

### Shape roles

- extraSmall: 8dp — text fields, selectors, compact menus.
- small: 12dp — chips and compact badges.
- medium: 16dp — cards and repeated financial containers.
- large: 24dp — larger branded/transient surfaces where Material does not own a stronger role.
- extraLarge: 28dp — dialogs/large transient surfaces where explicitly overridden.

### Typography

| Role | Size | Line height | Weight | Letter spacing |
| --- | ---: | ---: | --- | ---: |
| headlineLarge | 30sp | 36sp | Bold | -0.4sp |
| headlineMedium | 25sp | 31sp | Bold | -0.25sp |
| headlineSmall | 21sp | 27sp | SemiBold | -0.15sp |
| titleLarge | 19sp | 25sp | SemiBold | Material default |
| titleMedium | 16sp | 22sp | SemiBold | Material default |
| bodyLarge | 16sp | 23sp | Normal | Material default |
| bodyMedium | 14sp | 20sp | Normal | Material default |
| labelLarge | 14sp | 18sp | SemiBold | Material default |

Other Material typography roles inherit the current Material 3 theme unless a retained product surface proves a need for an override.

### Contrast

- Normal/small text: >=4.5:1 against the actual background.
- Large text and essential control/graphic boundaries: >=3:1.
- Decorative dividers may use lower contrast only when the divider is not necessary to recognize an interactive control or state.
- Financial color never carries meaning alone; label/icon/copy must also communicate the semantic role.

## Shared branded components

### Screen header — MyFinHub owned

- Full width.
- Horizontal padding: 20dp.
- Top padding: 16dp.
- Bottom padding: 8dp.
- Navigation/title/trailing group gap: 12dp.
- Title/subtitle gap: 4dp.
- Title: headlineSmall.
- Subtitle: bodyMedium / onSurfaceVariant.
- Brand mark default: 36dp.
- Navigation and trailing interactive targets: >=48dp.

### Section card / action card — MyFinHub owned

- Shape: medium / 16dp.
- Content padding: 16dp.
- Elevation: 0dp.
- Border: 1dp outlineVariant; decorative only.
- Internal default grouping: 8–12dp depending on content relationship.
- Clickable cards retain Material interaction/ripple behavior.

### Icon badge — MyFinHub owned

- Container: 40×40dp.
- Icon: 20×20dp.
- Shape: small / 12dp.
- Container/accent colors use the semantic finance pair.
- Badge itself is not assumed to be interactive; if clickable, surrounding target must be >=48dp.

### Finance row — MyFinHub owned geometry

- Full width.
- Horizontal padding: 16dp.
- Vertical padding: 12dp.
- Leading badge: 40dp.
- Leading/content gap: 12dp.
- Content micro-gap: 2dp.
- Trailing amount separated by at least 8dp.
- Card shape/border/elevation follow Section Card.
- No fixed row height: multi-line financial content may grow with font scale. Do not clip to a desktop-like fixed height.

### Primary finance action — deliberate MyFinHub override

Material baseline buttons are smaller than the desired visible finance action. MyFinHub therefore owns:

- Minimum visible height: 48dp.
- Horizontal content padding: 20dp.
- Vertical content padding: 12dp.
- Icon: 18dp.
- Icon/label gap: 8dp.
- Label: labelLarge.
- Shape/state/ripple/elevation: inherit Material filled Button defaults unless a later component-specific requirement is documented.

### Standard icon button — MyFinHub owned touch geometry

- Interactive target: 48×48dp minimum.
- Standard icon: 20dp.
- Content description required for non-decorative icon-only actions.
- Background/state treatment inherits Material IconButton unless explicitly branded.

## Form controls

### Outlined text field — MyFinHub geometry + Material behavior

- Minimum height: 56dp.
- Full-width on compact forms unless the field is semantically short and proven safe at large font scale.
- Shape: extraSmall / 8dp.
- Unfocused border: Material baseline 1dp.
- Focused/error border: Material baseline 2dp.
- Input text: bodyLarge.
- Supporting/error text: Material supporting slot; error text must also set accessibility error semantics.
- Monetary inputs use decimal keyboard.
- Person/labels use word capitalization; notes use sentence capitalization.
- Error state must preserve user input and move focus toward the first invalid field after Save where applicable.

### Selector field — MyFinHub owned form geometry

- Label: labelLarge.
- Label/control gap: 4dp.
- Outlined selector button minimum height: 56dp.
- Shape: extraSmall / 8dp.
- Full width on compact forms.
- Error copy: bodySmall/error and accessibility error semantics.
- Disabled state only when there are genuinely no choices; disabled styling inherits Material.

### Date field

- Same geometry as outlined text field.
- Manual YYYY-MM-DD remains keyboard-accessible fallback.
- Trailing date-picker icon button: 48dp target / 20dp icon.
- DatePicker and DatePickerDialog retain Material 3 layout/state defaults.

### Filter chip — intentional Material inheritance

- Container height: 32dp.
- Leading icon: 18dp.
- Shape role: small.
- State/ripple/disabled behavior: Material 3.
- MyFinHub only overrides semantic selected colors.
- Chip is not used as a substitute for a 48dp primary action; Material minimum interactive sizing remains enabled.

### Switch — intentional Material inheritance

- Visual track: 52×32dp.
- Track outline: 2dp when unselected.
- Selected handle: 24dp.
- Unselected handle: 16dp.
- Pressed handle follows Material token state.
- Material `minimumInteractiveComponentSize()` remains enabled, so the visual switch does not reduce the interactive target below accessibility requirements.
- Text label/state semantics belong to the row/switch owner.

## Navigation and primary floating action

### Bottom navigation — intentional Material inheritance

The app uses `NavigationSuiteScaffold`; for the compact phone bottom bar, retain Material navigation geometry:

- Container: 80dp.
- Icon: 24dp.
- Active indicator: 64×32dp.
- Indicator-to-label gap: 4dp.
- Item horizontal padding: Material 8dp.
- Indicator vertical offset: Material 12dp.
- Label hiding at large font scale remains a MyFinHub adaptive behavior to protect compact-phone layout; icon content descriptions remain mandatory.

Do not hard-code these dimensions into individual destination items.

### Compact Home FAB — intentional Material inheritance

For large-font compact Home, use the baseline Material FAB:

- Visual container: 56×56dp.
- Icon: 24dp.
- Shape/elevation/state: Material FAB baseline.
- Content description: `Νέα κίνηση`.

At normal font scale, Home uses the MyFinHub Primary Finance Action instead of duplicating a second custom extended-FAB geometry.

## Repeated lists

Where native Material ListItem is used, inherit baseline row heights:

- one line: 56dp;
- two line: 72dp;
- three line: 88dp;
- leading/trailing icon: 24dp;
- leading/trailing spacing: 16dp.

Custom finance rows are exempt from fixed ListItem heights because amounts, supporting copy and font scaling require content-driven height; they must instead follow the Finance Row padding contract.

## Dialogs, sheets and transient feedback

- AlertDialog / DatePickerDialog: inherit Material 3 component geometry unless a screenshot proves a retained-product issue. Do not apply global fixed width on phone.
- ModalBottomSheet: inherit Material container geometry and system inset behavior. MyFinHub may remove the drag handle when it creates an accessibility target defect, provided back/swipe/explicit-close paths remain.
- Snackbar: inherit Material sizing; placement must remain scaffold/inset-aware and never overlap bottom navigation/FAB.
- System-state cards use Section Card geometry; primary recovery actions use >=48dp visible height.

## Implementation rule

A screen-level `dp` value is allowed only when it describes real screen composition that cannot be expressed by one of these tokens (for example, a validated layout breakpoint). Core control geometry, screen edges, card padding, repeated-row padding, icon sizing, field sizing and action sizing must come from shared MyFinHub tokens/components or be intentionally inherited from Material 3.
