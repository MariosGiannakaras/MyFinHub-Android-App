# Android user-journey and element-level UX audit

Permanent review rules for the staged audit (00–08); progress belongs in the
canonical project tracker, not in this document or conversation history.

## Canonical product scope

Read AGENTS.md and docs/ADR-0002-mobile-top-level-navigation.md first:
Android daily-use navigation has **three** roots (Αρχική, Κινήσεις, Κάρτες).
Device/app Settings are secondary. Plan/Insights and Web finance
administration remain internal/test-only, not extra mobile tabs.
The three full Home primary account cards retain their charts and account
actions. Do not weaken data/security/auth/offline/signing invariants.

## Review sequence

00: capture evidence from existing official Compose screenshots, CI provenance
and per-journey checklist. 01: Cards, chosen card detail, purchase/payment and
secure details. 02: Quick Entry kinds, validation and Save/Cancel.
03: Activity search/filter/details/edit and return. 04: Home, account cards,
balance/privacy/attention and shortcuts. 05: Account details, savings, loans,
lending and contextual secondary paths. 06: Settings, updates, PIN/biometrics,
auth/logout. 07: legacy routes and Web-only administration exposure. 08:
integrated cross-route tests, visual/accessibility review, separate real S24
owner acceptance and later protected release.

## For EVERY visible and interactive element

Inspect: (1) why the user entered this page, (2) what they are trying to
achieve, (3) meaning of the control/label, (4) whether the task is already
available in a more natural place, (5) source identity and consequences,
(6) screen after action/confirmation, (7) Back/cancel/return, and
(8) empty/loading/offline/error/long-font/privacy states. Classify
**keep, relocate, consolidate or remove** with explicit code AND interaction
evidence; never remove behavior solely from a screenshot.

First grounded case in Chat 01: CanonicalCardDetailScreen.kt exposes
**Αλλαγή κάρτας**, while card selection exists in parent Cards; MyFinHubApp.kt
currently pushes another CardDetail when changing card. Analyze the
back stack and preserve the originally chosen card on return. Inspect and
correct this only after cross-screen navigation tests, not as an isolated
cosmetic change.

## Evidence and completion

The Android UI Quality job validates official screenshot goldens and now
uploads review.html + review-index.json + review-checklist.md with the PNGs.
The JSON includes exact source head, CI run ID, image SHA-256 and baseline
variant gaps. Run the narrowest real user-journey tests first, then all
required exact-head CI gates and merge reviewable workflow batches.

These images are **synthetic**. They prove visual fixtures, not that a
click works, payments persist, private values are hidden in screen recording,
or that the physical owner's Galaxy S24 Ultra passed D7. Protected D8 is
a separate release decision.
