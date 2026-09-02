# MyFinHub Android — Component pixel specification

Status: accepted implementation contract for issue #47

This document distinguishes **MyFinHub-owned values** from **intentional Material 3 inheritance**. A component must not copy framework token values into local screen code unless MyFinHub deliberately overrides them.

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

Derived layout relationships are named rather than repeated as magic numbers:

- Compact content clearance above persistent phone navigation: 96dp = 80dp navigation + 16dp spacing.
- Product Snackbar clearance when navigation and a primary finance action are present: 152dp = 80dp navigation + 48dp action + 24dp spacing.

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

- Normal/small text: >=4.5:1 against the actual rendered background.
- Large text and essential control/graphic boundaries: >=3:1.
- Decorative dividers may use lower contrast only when the divider is not necessary to recognize an interactive control or state.
- Financial color never carries meaning alone; label/icon/copy must also communicate the semantic role.
- Light and dark finance accent/container pairs and essential outlines are protected by unit contrast contracts.

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
- No fixed row height: multi-line financial content may grow with font scale.

### Primary finance action — deliberate MyFinHub override

Material baseline buttons are smaller than the desired visible finance action. MyFinHub owns:

- Minimum visible height: 48dp.
- Horizontal content padding: 20dp.
- Vertical content padding: 12dp.
- Optional icon: 18dp.
- Icon/label gap: 8dp.
- Label: labelLarge.
- Shape/state/ripple/elevation: Material filled Button defaults.

### Outlined secondary action

- Same 48dp minimum visible/action height and 20dp/12dp content padding as Primary Finance Action.
- Optional icon: 18dp with 8dp icon/label gap.
- Border/state/ripple/disabled behavior inherits Material OutlinedButton.

### Destructive text action

- Minimum interactive height: 48dp.
- Label: labelLarge.
- Foreground uses `colorScheme.error` so destructive intent is visually explicit.
- Ripple/state behavior inherits Material TextButton.
- Destructive meaning must also be present in the text; color is not the only signal.

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
- Supporting/error text: Material supporting slot; error text also sets accessibility error semantics.
- Monetary inputs use decimal keyboard.
- Person/labels use word capitalization; notes use sentence capitalization.
- Secure fields retain the same geometry while using the appropriate password visual transformation and keyboard type.
- Error state preserves user input and moves focus toward the first invalid field after Save where applicable.

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
- Material minimum interactive sizing remains enabled.

### Switch — intentional Material inheritance

- Visual track: 52×32dp.
- Track outline: 2dp when unselected.
- Selected handle: 24dp.
- Unselected handle: 16dp.
- Pressed handle follows Material token state.
- Material `minimumInteractiveComponentSize()` remains enabled.
- Text label/state semantics belong to the row/switch owner.

## Authentication surface

Authentication uses the same control contracts as the signed-in product rather than a separate visual system.

- Outer horizontal padding: 24dp.
- Outer vertical padding: 32dp.
- Content max width: 480dp.
- Main content rhythm: 16dp.
- Auth brand mark: 40dp.
- Email/password/TOTP/PIN fields: shared 56dp outlined-field geometry.
- Sign-in, verify, PIN enrollment/unlock and biometric actions: shared 48dp primary action geometry.
- PIN fallback secondary action: shared 48dp outlined action.
- Password/PIN visual transformation and security behavior remain owned by the auth/security layer.

## Production card-secret surface

The production canonical Card Detail route uses a dedicated secure surface; local/demo Money editor routes remain isolated from signed-in production.

- Header/back action follows the shared screen-header/icon-target contract.
- Summary and secure-state containers follow Section Card geometry.
- Secret label alignment column: 56dp, named as `secretValueLabelWidth` rather than repeated locally.
- Reveal, retry and device-local CVV save use shared 48dp primary actions.
- New CVV input uses the shared 56dp secure field with NumberPassword keyboard and PasswordVisualTransformation.
- Local CVV deletion uses the shared destructive text action.
- PAN/expiry remain owner+AAL2 server-vault values; CVV remains device-local encrypted state. No visual-system change alters that security boundary.
- `SecureWindowProtection` remains active only while real secret state is revealed.
- Canonical screenshot coverage uses sanitized fixtures only; no real or reusable PAN/CVV secret is committed to references.

## Navigation and primary floating action

### Bottom navigation — intentional Material inheritance

The app uses `NavigationSuiteScaffold`; for the compact phone bottom bar:

- Container: 80dp.
- Icon: 24dp.
- Active indicator: 64×32dp.
- Indicator-to-label gap: 4dp.
- Item horizontal padding: Material 8dp.
- Indicator vertical offset: Material 12dp.
- Label hiding at large font scale remains a MyFinHub adaptive behavior; icon content descriptions remain mandatory.

Compact scrollable surfaces that can coexist with navigation/FAB use the named 96dp bottom-clearance relationship instead of repeating a literal value.

### Compact Home FAB — intentional Material inheritance

For large-font compact Home, use the baseline Material FAB:

- Visual container: 56×56dp.
- Icon: 24dp.
- Shape/elevation/state: Material FAB baseline.
- Content description: `Νέα κίνηση`.

At normal font scale, Home uses the MyFinHub Primary Finance Action.

## Repeated lists

Where native Material ListItem is used, inherit baseline row heights:

- one line: 56dp;
- two line: 72dp;
- three line: 88dp;
- leading/trailing icon: 24dp;
- leading/trailing spacing: 16dp.

Custom finance rows are exempt from fixed ListItem heights because amounts, supporting copy and font scaling require content-driven height; they follow the Finance Row padding contract instead.

## Dialogs, sheets and transient feedback

- AlertDialog / DatePickerDialog: inherit Material 3 component geometry unless a retained-product issue is proven by rendering.
- ModalBottomSheet: inherit Material container geometry/system insets. MyFinHub may remove the drag handle when it creates an accessibility-target defect, provided back/swipe/explicit-close paths remain.
- Snackbar: inherit Material sizing. Signed-in product placement uses the named 152dp bottom-clearance relationship when persistent phone navigation and a primary action may coexist.
- System-state cards use Section Card geometry; primary recovery actions use shared >=48dp visible height.

## Screen-rhythm decisions

- Home and Activity compact content use the same 20dp horizontal screen edge and named navigation-content bottom clearance.
- Quick Entry uses 20dp compact edges, 12dp primary form grouping and content-driven field heights; split editor remains vertically scrollable rather than compressing controls.
- Canonical Money/Plan, Insights and Settings retain 20dp compact edges and tokenized 8/12/16/20/24dp section rhythm.
- Large-font layouts grow vertically and scroll; they are not forced into fixed desktop-like rows.
- Expanded/tablet-like composition breakpoints remain implementation-only layouts and are not supported-device acceptance targets.

## Implementation rule

A screen-level `dp` value is allowed only when it describes real composition that cannot be expressed by one of these tokens (for example, a validated layout breakpoint). Core control geometry, screen edges, card padding, repeated-row padding, icon sizing, field sizing, action sizing and persistent-surface clearances come from shared MyFinHub tokens/components or are intentionally inherited from Material 3.
