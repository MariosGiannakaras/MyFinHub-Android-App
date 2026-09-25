# Android UI/UX canonical findings

## Confirmed implementation gaps

### System bars
`MainActivity` enables edge-to-edge while the product surface does not consistently consume safe-drawing top insets. Physical S24 screenshots show top titles/content clipped under the status bar.

### Activity chronology
`CanonicalProductProjection.buildActivityState()` sorts `ActivityItem` only by the calendar `date` string. Canonical events also carry `createdAt`/`updatedAt`, so same-day activity currently has no deterministic newest-first timestamp ordering.

### Category/subcategory parity
Central `develop` stores canonical transaction taxonomy in `FinanceSettings.expenseCategoryTree` / `incomeCategoryTree`; `FinanceEvent` has both `category` and `subcategory`.

Android already reads the expense tree and event subcategories for Quick Entry choices, but the subcategory selector is hidden behind additional details and selectors are cycle-buttons rather than explicit lists. Android event creation already writes canonical category/subcategory fields for supported entry kinds.

### Edit parity
Central `develop` edits a `FinanceEvent` through the same full QuickAdd form and preserves `id`/`createdAt` while updating `updatedAt`. Android `EditCanonicalActivity` currently edits only `note` and `category`, leaving date/subcategory/account and other canonical fields unavailable.

### Date input
Central uses a dedicated date-input component. Android Quick Entry uses a raw `YYYY-MM-DD` text field and transaction editing has no date picker.

### Post-save navigation
Central QuickAdd closes after `onCreate`. Android keeps the Quick Entry route open in a pending state after enqueue.

### Home/account interaction
Home account rows are presentation-only and there is no AccountDetail route. This prevents normal drill-down to account-specific activity.

### Amount visibility
Appearance is persisted in Settings, but Home amount visibility is screen-local state and Settings has no persisted amount-display preference.

### Card creation
Central `develop` supports canonical upsert of `PaymentCard` records in `state.cards`. Android currently only has `DeactivateCanonicalCard`; no canonical card-upsert mutation or add-card route exists. Card-secret/PAN/CVV data must remain outside the finance document.

### Notice duplication
The app root still consumes product notices through a Snackbar with a `Λεπτομέρειες` action. This competes with newer pending/status presentation and produces stacked/duplicate transient surfaces on the physical device.

## Shared canonical fields verified in central develop

`FinanceEvent`: `id`, `date`, `kind`, `amount`, `note`, `category`, `subcategory`, `accountId`, `fromAccountId`, `toAccountId`, `person`, `legs`, `parts`, `cardId`, `createdAt`, `updatedAt` and related finance deltas.

`PaymentCard`: stable `id`, `bankId`, `nickname`, `kind`, `network`, optional `formFactor`, `designId`, `holderName`, `last4`, `vaultRef`, credit-statement settings, `active`, `createdAt`, `updatedAt`.

`FinanceSettings`: account names/default accounts, expense/income categories and category trees, category identities/preferences and other user settings.

These fields are the source of truth for the Android correction pass; Android-local icons remain presentation-only.
