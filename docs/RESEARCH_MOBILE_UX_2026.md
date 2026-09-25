# 2026 Android mobile UX research

Status: Phase 0 research baseline  
Date: 2026-08-22  
Tracker: #1

## Purpose

This document answers a narrow question before MyFinHub Android implementation begins: **how should a complex finance application be translated from an established web/desktop product into a modern Android experience in 2026 without shrinking the desktop interface?**

The answer is not to reproduce page geometry. The Android client should preserve MyFinHub capabilities, finance semantics, information relationships, and security model while redesigning interaction flows for touch, compact windows, Android navigation, and adaptive larger windows.

## Executive findings

1. **Compose-first is the correct baseline.** Current Android guidance explicitly recommends Compose and Material 3 Adaptive for modern adaptive applications. Material 3 Adaptive 1.3.0 is stable as of August 2026 and integrates with Navigation 3 scene strategies.
2. **Design for the current app window, not a device category.** Phone/tablet/foldable assumptions are secondary. Window size classes and posture drive layout; orientation and aspect ratio should not be locked.
3. **Compact mobile navigation must be selective.** Android guidance puts 3-5 equal top-level destinations in a navigation bar. Secondary domains belong behind contextual navigation, tabs, lists, search, or drill-down—not as a desktop sidebar copied into a drawer.
4. **Complexity is preserved through progressive disclosure.** Dense desktop tables become scan-friendly rows/cards plus detail screens; multi-column workspaces become list-detail; complex forms become staged, vertically ordered editors; secondary actions move into contextual menus or bottom sheets.
5. **Mobile dashboards are glance/action surfaces, not miniature BI canvases.** Successful finance apps surface balances, recent/upcoming activity, alerts, and a small number of decision-driving charts first, then drill into details.
6. **High-frequency actions deserve multiple fast entry points.** YNAB is a strong example: transaction entry exists from several major tabs, long-press category actions, app-icon shortcuts, and widgets. The capability is the same, but the invocation is optimized for mobile context.
7. **Search is navigation in complex apps.** Monzo, Slack, and GitHub increasingly expose global search prominently because deep hierarchical navigation becomes costly on mobile.
8. **State continuity matters.** GitHub Mobile's 2026 Android navigation refresh explicitly emphasizes persistent bottom navigation and preserving a user's place per tab. MyFinHub should preserve tab/back-stack state rather than resetting users whenever they switch domains.
9. **Android-native behavior is part of UX quality.** Edge-to-edge, predictive back, system insets, IME behavior, system font scaling, TalkBack semantics, and 48dp touch targets are not polish tasks; they are baseline implementation requirements.
10. **Accessibility cannot depend on gestures or color alone.** Every essential swipe/long-press action needs a discoverable/accessibility alternative. Finance status must remain understandable with color differentiation disabled.

---

## 1. Current Android platform direction

### 1.1 Compose + Material 3 Adaptive

Android's current adaptive guidance says to build with Compose and use Material 3 Adaptive APIs. `NavigationSuiteScaffold` adapts primary navigation, while `ListDetailPaneScaffold` and `SupportingPaneScaffold` implement canonical multi-pane patterns. Current guidance explicitly warns against fixed aspect ratios and device-size assumptions.

Material 3 Adaptive 1.3.0 (released 2026-08-12) adds stable integration with Navigation 3 scene strategies for list-detail and supporting-pane layouts, plus improved edge-to-edge behavior.

Implication for MyFinHub:

- Compact window: one primary pane at a time.
- Medium/expanded window: show related list/detail or primary/supporting content side by side when it reduces navigation cost.
- Fold/posture information may alter panes without creating an entirely separate app architecture.
- A phone rotated or a tablet in split-screen is treated by available window space, not by a static `isTablet` flag.

Primary references:

- https://developer.android.com/develop/adaptive-apps/guides/adaptive-dos-and-donts
- https://developer.android.com/develop/ui/compose/build-adaptive-apps
- https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
- https://developer.android.com/guide/navigation/navigation-3/recipes/material-listdetail

### 1.2 Primary navigation

Android's 2026 layout/navigation guidance says a navigation bar is appropriate for **three to five stable destinations at the same hierarchy level** on compact windows. The equivalent can become a navigation rail on larger windows. Drawers can hold more destinations but are less ideal as the primary compact navigation because they are less reachable and add an extra navigation step.

Implication for MyFinHub:

- Do not reproduce the web sidebar as a hamburger drawer containing every page.
- Choose a small set of durable top-level mental models.
- Put secondary capabilities under the most natural parent domain and make global search available.
- Settings can live under profile/account chrome rather than consuming a bottom destination.

References:

- https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns
- https://developer.android.com/develop/ui/compose/components/navigation-bar
- https://developer.android.com/develop/ui/compose/components/navigation-rail

### 1.3 Back navigation

Predictive Back is an expected Android behavior. On Android 15+ supported system animations are enabled by default for apps that use supported APIs. Compose and Navigation support predictive back; components such as `ModalBottomSheet` integrate with it.

Implication:

- One activity and proper navigation back stacks are preferred.
- Do not intercept root Back globally unless required; doing so can break platform back-to-home behavior.
- Sheets, dialogs, detail pages, and nested editors must close/pop in the order users can preview through the back gesture.

References:

- https://developer.android.com/develop/ui/compose/system/predictive-back
- https://developer.android.com/develop/ui/compose/system/predictive-back-setup

### 1.4 Edge-to-edge and system insets

Edge-to-edge is enforced by default for apps targeting Android 15 / API 35+ on relevant devices. Material components expose inset-aware behavior, but application content must still consume scaffold/window insets correctly.

Implication:

- The layout must be designed from the start around status/navigation bars and IME insets.
- Bottom navigation, sheets, transaction editors, and numeric entry must not be retrofitted after feature work.

References:

- https://developer.android.com/develop/ui/compose/system/setup-e2e
- https://developer.android.com/develop/ui/compose/system/material-insets

---

## 2. Translation rule: move user jobs, not desktop geometry

A desktop/web screen often gains efficiency from persistent sidebars, multiple columns, hover states, wide tables, visible filters, and simultaneous editor/detail panes. These are not inherently better patterns; they are adaptations to mouse/keyboard and large windows.

For MyFinHub Android, every screen should be decomposed into:

1. the user's primary question or job;
2. the minimum information needed to decide;
3. the primary action;
4. supporting/secondary context;
5. infrequent or destructive actions.

Then remap those layers to mobile components.

### Desktop-to-mobile pattern map

| Desktop/web pattern | Compact Android translation | Medium/expanded translation |
| --- | --- | --- |
| Persistent left sidebar | 3-5 destination bottom navigation + contextual child navigation/search | Navigation rail + optional multi-pane content |
| Dense dashboard grid | Ordered vertical summary sections; high-value KPIs first; drill-down cards | Two-column/supporting-pane layout where relationships remain clear |
| Wide transaction table | Lazy list with prioritized fields; filters/chips; row -> detail/editor | List-detail scaffold, optional extra context pane |
| Always-visible table columns | Primary values inline; secondary metadata on second line/detail | Restore additional columns only when width supports them |
| Multi-column form | One vertical task flow, logical groups, progressive disclosure | Two-column grouping only when scan/focus order remains logical |
| Desktop modal containing full workflow | Bottom sheet for bounded secondary choices; dedicated screen for complex editing | Dialog/sheet/supporting pane depending task size |
| Hover actions | Visible icon/overflow/context actions; optional long-press accelerator | Pointer/hover can enhance, never become required |
| Toolbar with many actions | One primary action + contextual overflow | More actions may become visible with available width |
| Horizontal analytics panel | One focused chart per section + segmented timeframe/filter | Side-by-side comparison/supporting chart where useful |
| Sidebar filters | Chips/search + filter sheet | Persistent filter pane only if width justifies it |

The default should not be horizontal scrolling for a desktop-style data grid on compact screens. If a value is important enough for routine comparison, it should be prioritized in the row design. Remaining values belong in detail, expand/collapse, or a specialized comparison view.

---

## 3. Finance and complex-product examples

These examples are used for interaction principles, not visual copying.

### 3.1 Monzo: unified home + contextual depth

Current Monzo help describes a Home screen that puts accounts at the top, account/card actions next to the relevant object, activity directly below, savings Pots in their own area, and a top-right `+` entry point for creating/opening financial products. Trends is reached from Home and then splits into focused Balance, Spending, and Target views. Monzo search is unified and available from Home/Help, with transaction filters and direct actions from results.

Pattern lessons:

- Home is an overview plus action surface, not a directory of every feature.
- Object-specific actions live near the object (card freeze/details next to the account/card).
- Analytics is summarized in Home, then expanded into dedicated focused views.
- Search crosses data types rather than forcing users to remember which section owns an item.

References:

- https://monzo.com/ie/help/using-monzo/help-home-screen
- https://monzo.com/ie/help/managing-money/help-trends
- https://monzo.com/help/app-help/unified-search-changes-web
- https://monzo.com/blog/how-we-unified-our-customers-activity-on-the-new-home-screen

### 3.2 Revolut: broad capability grouped into a small mental model

Revolut is much broader than a basic bank account—payments, cards, savings/investment products, currency, and other financial services coexist in one app. Its historical navigation redesign is a useful principle: many features were grouped under a small number of high-level destinations such as Home, Wealth, and Payments. Current product/help pages continue to describe Home and bottom-menu entry rather than a flat list of every capability. Card controls are exposed directly in the mobile card experience.

Pattern lessons:

- A feature-rich finance app can remain navigable by grouping around user intent instead of exposing one primary tab per feature.
- High-risk/high-frequency card actions should be direct and contextual.
- Product expansion should not cause primary navigation to grow indefinitely.

References:

- https://www.revolut.com/blog/post/introducing-revolut-7-0/
- https://help.revolut.com/el-GR/help/app-features/vaults/i-cannot-find-my-vaults/
- https://www.revolut.com/el-GR/ways-to-bank/mobile-banking/
- https://www.revolut.com/el-GR/cards/bank-cards/

### 3.3 YNAB: capability parity with mobile-specific invocation

YNAB is particularly relevant because it has complex budgeting/account-register workflows on web and mobile. In 2026 YNAB continues to improve Android transaction entry rather than cloning the web register. The mobile app exposes Add Transaction from several major tabs and account registers, supports category long-press actions, app-icon shortcuts, and Android home-screen widgets. Its Reflect experience turns reports into focused spending/income views suited to mobile.

Pattern lessons:

- High-frequency entry should be reachable from several relevant contexts.
- Mobile can add platform-native accelerators without changing finance semantics.
- A desktop report surface can become a set of focused reflection/insight views rather than a squeezed chart dashboard.
- Transaction types can be explicit in the mobile editor, reducing accounting ambiguity compared with generic inflow/outflow entry.

References:

- https://support.ynab.com/en_us/how-to-add-transactions-in-ynab-HyDwA_byi?mobile-help=true
- https://support.ynab.com/en_us/ynab-widget-for-mobile-a-guide-HJPEEQYR9?mobile-help=true
- https://support.ynab.com/en_us/updates-to-ynab-S1f4aRLeC
- https://www.ynab.com/whats-new/the-clearest-way-to-enter-transactions
- https://www.ynab.com/whats-new/spending-breakdown-on-mobile

### 3.4 Copilot Money: dashboard as decision summary

Copilot's 2026 Dashboard documentation describes one focused top graph around monthly spending/free-to-spend, followed by new transactions, trending categories, upcoming recurring items, and net income. This is a useful pattern for MyFinHub because it mixes one decision-driving visualization with immediately actionable/temporal finance content.

Pattern lessons:

- One chart can anchor a dashboard when it answers a clear question.
- Upcoming obligations and new/unreviewed activity belong high in a personal-finance dashboard.
- Analytics should lead to actions or drill-down, not exist as decorative density.

Reference:

- https://help.copilot.money/en/articles/6045480-dashboard-tab-overview

### 3.5 GitHub Mobile and Slack: preserve complexity through focus and search

GitHub Mobile's March 2026 Android navigation refresh emphasizes persistent bottom navigation, smoother switching between key areas, and preserving each tab's place. Its April 2026 Copilot update moves a complex new domain into a primary tab with a summary home, filtered task list, detailed session logs, and direct actions—all native rather than a web page embed.

Slack's mobile design work emphasizes short bursts of mobile work, thumb reachability, fewer taps, content-first layouts, global search, and controls appearing near the action rather than reproducing the desktop workspace. These apps demonstrate that very complex desktop products can preserve core capability while changing information architecture and interaction density on mobile.

References:

- https://github.blog/changelog/2026-03-20-a-smoother-navigation-experience-in-github-mobile-for-android/
- https://github.blog/changelog/2026-04-01-github-mobile-stay-in-flow-with-a-refreshed-copilot-tab-and-native-session-logs/
- https://slack.com/blog/news/redesigning-slack-ios26
- https://slack.com/blog/productivity/a-redesigned-slack-built-for-focus

---

## 4. MyFinHub-specific pattern decisions

### 4.1 Dashboard

The Android dashboard should preserve the current semantic priority—cash/current/savings and other balances, actionable/attention items, Quick Entry, then analytics—but render it as a vertical decision flow.

Compact principles:

- Use a clear financial summary block rather than a dense grid.
- Surface Needs Attention/upcoming obligations close to the top when non-empty.
- Quick Entry must remain one-tap reachable.
- Prefer one focused chart per viewport section.
- Cards should navigate to a focused detail screen; do not embed full desktop subviews.
- Allow high-value sections to be reordered only if this does not undermine established financial hierarchy.

### 4.2 Transactions and Review

Transactions become a high-performance lazy list. Each row should prioritize:

- amount;
- category/type;
- account/card context when needed;
- date/time or relative date;
- review/scheduled/transfer state.

Secondary metadata belongs on a second line or detail page. Search/filter is first-class. Selection/bulk operations may enter an explicit contextual mode rather than adding permanent row controls.

Review should be integrated into the Activity mental model but remain a distinct workflow. Suggestion -> inspect -> confirm/keep/edit must remain explicit and auditable.

### 4.3 Desktop-style tables

No generic `DataTable` abstraction should be built merely to mimic the web app. Each data set gets a compact row model based on the user's comparison task.

Examples:

- Loans: lender/name + remaining amount + next obligation; full terms in detail.
- Recurring: title + next date + expected amount + status; editing in detail.
- Cards: visual identity + bank + type + status; secure controls in card detail.
- Rules: rule summary + enabled state; conditions/actions in editor.
- Budgets: category + spent/limit + progress; edit via detail/sheet.

On wider windows the same data model can use list-detail or selectively expose extra columns.

### 4.4 Forms and editors

Complex transaction/payment/edit forms should use vertical grouped sections with the most consequential choices early. Use the correct IME/numeric keyboard hints. Hide fields that are irrelevant to the selected transaction type instead of disabling a desktop matrix of inputs.

Rules:

- Explicit transaction type first where semantics differ materially.
- Validate close to the field but summarize blocking errors at submission when necessary.
- Preserve entered state through navigation/configuration changes.
- Destructive actions are visually separated from save/confirm.
- For a short secondary choice list, use a sheet; for a multi-step or high-risk editor, use a dedicated screen.

### 4.5 Sheets, dialogs, menus

Material bottom sheets are appropriate for secondary content/action lists that need more room than a menu. They should not become a universal container for every form.

Use:

- overflow menu: short, simple, low-risk actions;
- modal bottom sheet: filters, account/category pickers, action lists, short bounded edits;
- dialog: confirmation/critical decision where immediate interruption is appropriate;
- full screen/destination: complex data entry, secure card editing, multi-step flows.

Reference:

- https://developer.android.com/develop/ui/compose/components/bottom-sheets

### 4.6 Charts

Charts must answer a named question and remain readable at system font scaling and compact widths.

Rules:

- Avoid several tiny charts in one phone viewport.
- Provide a text/KPI summary that communicates the chart's conclusion.
- Time range and metric switching should use compact controls (segmented buttons/tabs/chips as appropriate).
- Detailed category comparison may use ranked lists alongside/below charts rather than forcing labels into the visualization.
- Accessibility semantics or an equivalent data summary are required; color alone must not encode state.

### 4.7 Quick actions

Quick Entry is the strongest candidate for a primary FAB/contextual action. The Android implementation should also leave room for later platform accelerators such as an app-icon shortcut or widget, but those are additive and must call the same finance workflow.

Do not introduce multiple competing global FABs.

### 4.8 Search

Search should span transactions and other privacy-safe searchable entities. Results must be grouped by type and allow direct navigation/action when safe. Search is a navigation accelerator, not just a transaction filter.

### 4.9 Gesture policy

Gestures may accelerate but not hide capability.

Allowed examples:

- swipe to reveal an already-available action;
- long-press to open contextual actions;
- pull-to-refresh if a refresh concept is meaningful.

Not allowed:

- swipe-only delete/archive;
- long-press-only critical finance action;
- hidden gesture required to reveal security controls.

Android accessibility guidance explicitly says not to rely on gestures for all actions and recommends an alternate affordance.

Reference:

- https://developer.android.com/design/ui/mobile/guides/foundations/accessibility

---

## 5. Accessibility baseline

Accessibility is part of the component contract from the first implementation.

### Required minimums

- Every interactive target: at least 48dp x 48dp.
- Small text contrast: at least 4.5:1; large text/graphics: at least 3:1 under Android quality guidance.
- Meaningful icons have accessible descriptions; decorative icons are excluded from the semantics tree.
- Status is never represented only by red/green or another color pair.
- Logical TalkBack traversal follows the same task order as the visual hierarchy.
- Custom financial visualizations expose useful semantics/text summaries.
- Dynamic/system font scaling must not clip amounts, labels, buttons, or navigation.
- Essential actions are keyboard/focus reachable on larger devices.
- Animations respect system motion/accessibility expectations where applicable.

Compose Material/Foundation components already provide useful semantics by default; custom components must add or merge semantics deliberately.

References:

- https://developer.android.com/guide/topics/ui/accessibility/apps.html
- https://developer.android.com/develop/ui/compose/accessibility
- https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- https://developer.android.com/develop/ui/compose/accessibility/semantics
- https://developer.android.com/develop/adaptive-apps/quality-guidelines/core-app-quality

---

## 6. Adaptive device strategy

The Android app should support compact phones first, but its architecture must not make larger windows an afterthought.

### Compact

- bottom navigation;
- one content pane;
- dedicated list/detail screens;
- sheets for secondary controls;
- vertical forms and charts.

### Medium

- navigation may become rail/short navigation depending available space;
- list-detail can appear together;
- supporting analytics/filter panes may appear when useful;
- avoid merely stretching cards/buttons to full width.

### Expanded / large / foldable unfolded

- navigation rail;
- canonical list-detail/supporting-pane layouts;
- additional context visible without duplicating navigation state;
- sensible max content widths so text/forms do not stretch indefinitely.

A single navigation/data model should adapt its presentation, not fork into separate phone/tablet implementations.

---

## 7. Performance implications

Compose performance should be measured in release/benchmark conditions, not inferred from debug mode.

Planned approach:

- LazyColumn/LazyGrid with stable keys for long finance lists.
- Hoist and memoize derived UI state; avoid rescanning large legacy transaction data during unrelated recompositions.
- Use app-specific Baseline Profiles for startup, Home, transaction scrolling, Quick Entry, and common detail flows.
- Use Macrobenchmark for startup/scroll/jank and compare Baseline Profile benefit.
- Use R8 in release builds.

Android documentation reports that app-specific Baseline Profiles can materially improve first-run execution and recommends Macrobenchmark for critical user journeys.

References:

- https://developer.android.com/develop/ui/compose/performance
- https://developer.android.com/develop/ui/compose/performance/baseline-profiles
- https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview

---

## 8. Testing implications from the design research

A mobile-first UI requires more than unit tests.

The GitHub CI plan should include:

- Compose component/feature tests against semantics;
- official Compose Preview Screenshot tests for visual regressions;
- screenshot/reference configurations representing compact, medium, expanded, light/dark, and selected large-font states;
- instrumented tests on build-managed virtual devices for navigation, keyboard/insets, back behavior, and end-to-end feature flows;
- accessibility assertions where automatable;
- no test fixture containing real finance or card data;
- release Macrobenchmark/Baseline Profile generation for critical journeys.

Android explicitly recommends screenshot testing for visual attributes in Compose, and Gradle/build-managed devices are designed for reproducible device matrices in CI.

References:

- https://developer.android.com/training/testing/ui-tests/screenshot
- https://developer.android.com/studio/preview/compose-screenshot-testing
- https://developer.android.com/studio/test/managed-devices
- https://developer.android.com/training/testing/different-screens/tools

---

## 9. Research conclusions that are now project constraints

The following are considered settled unless new evidence forces an ADR change:

1. Native Kotlin + Jetpack Compose; no WebView or cross-platform UI framework.
2. Material 3 + Material 3 Adaptive baseline.
3. Window-size/posture adaptive design; no orientation lock.
4. Compact primary navigation limited to a small stable set rather than a copied desktop sidebar.
5. Preserve top-level navigation state/back stacks.
6. Progressive disclosure for complex desktop functionality.
7. Dedicated mobile transaction/payment editors instead of ported desktop forms.
8. Compact financial tables become task-specific lists + detail; wider layouts may restore additional information.
9. Bottom sheets are secondary/action surfaces, not a substitute for every page.
10. Quick Entry remains a first-class fast action.
11. Search is global navigation/search infrastructure.
12. 48dp touch target and accessibility semantics are implementation gates.
13. Gesture-only finance actions are prohibited.
14. Compact-first implementation must still be adaptive for tablets/foldables/windowed Android.
15. Visual parity with web is subordinate to product identity, finance semantics, and native Android usability.

## 10. Open design validation items

These require prototype evidence before becoming final screen architecture:

- Exact 4-vs-5 top-level destination model.
- Which Planning/Accounts/Insights subdomains deserve persistent child tabs versus grouped lists.
- Which Dashboard sections merit compact charts versus KPI/list representations.
- Which transaction row metadata stays visible at compact width.
- Where Quick Entry FAB remains persistent versus contextual.
- Which secure card actions require biometric re-authentication beyond the existing AAL2 session.

The next Phase 0 document converts these findings into the initial MyFinHub Android design contract and prototype targets.