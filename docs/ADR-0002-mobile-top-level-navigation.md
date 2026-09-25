# ADR-0002: Keep five top-level mobile destinations

Status: Accepted for Phase 2 parity validation

## Decision
Keep five top-level destinations for the native Android client:

1. Αρχική (Home)
2. Κινήσεις (Activity)
3. Χρήματα (Money)
4. Πλάνο (Plan)
5. Αναλύσεις (Insights)

## Rationale
The five destinations map to distinct user jobs and avoid recreating desktop navigation inside a mobile drawer:

- **Home** answers “what needs my attention now?” and provides the shortest path to Quick Entry.
- **Activity** owns transaction search, review/detail/edit and drill-down from analytical views.
- **Money** groups accounts, savings, cards, credit, loans and lending around positions/assets/liabilities.
- **Plan** groups recurring/scheduled obligations, budgets and forward cash-flow decisions.
- **Insights** is read-oriented reporting/trends and links back to supporting Activity data.

Collapsing to four destinations would force either Plan into Insights or Money into Home. Both combinations mix different task modes, increase nested navigation and make the primary mobile information architecture less predictable.

## Adaptive behavior
NavigationSuiteScaffold remains responsible for bottom-bar / navigation-rail adaptation. Labels may be hidden at large font scale where space requires it, but destination semantics and accessible labels remain unchanged.

## Constraints
- Five top-level destinations do not imply five independent data stores; all production features project from the same canonical FinanceData state.
- Detail/editor routes remain nested Navigation 3 destinations rather than additional top-level tabs.
- This ADR can be revisited only with usability evidence showing a concrete reduction in navigation cost, not solely to match a generic four-tab convention.
