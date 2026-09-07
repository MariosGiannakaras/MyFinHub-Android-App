from pathlib import Path
import re


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}: {old[:80]!r}; got {count}")
    p.write_text(text.replace(old, new, 1))


replace_once(
    "app/src/main/res/values/strings.xml",
    '<string name="nav_money">Χρήματα</string>',
    '<string name="nav_money">Περιουσία</string>',
)
replace_once(
    "app/src/main/res/values/strings.xml",
    '<string name="nav_insights">Αναλύσεις</string>',
    '<string name="nav_insights">Εικόνα</string>',
)

# HOME — one primary value; attention and due work precede secondary context.
home_path = Path("app/src/main/java/app/myfinhub/android/feature/home/ProductionHomeScreen.kt")
home = home_path.read_text()
old_order = """            item { FinancialSnapshotCard(state = state, amountsVisible = amountsVisible, onOpenQuickEntry = onOpenQuickEntry) }
            if (state.attentionItems.isNotEmpty()) item { ProductionAttentionCard(state.attentionItems, onOpenAttention) }
            item { PrimaryAccountsCard(state.accounts.take(3), amountsVisible, onOpenAccount) }
            item { RecentActivityCard(state.recentItems, amountsVisible, onOpenRecent) }
            if (state.upcomingItems.isNotEmpty()) item { ProductionUpcomingCard(state.upcomingItems, amountsVisible) }"""
new_order = """            item { FinancialSnapshotCard(state = state, amountsVisible = amountsVisible, onOpenQuickEntry = onOpenQuickEntry) }
            if (state.attentionItems.isNotEmpty()) item { ProductionAttentionCard(state.attentionItems, onOpenAttention) }
            if (state.upcomingItems.isNotEmpty()) item { ProductionUpcomingCard(state.upcomingItems, amountsVisible) }
            item { MonthContextCard(state = state, amountsVisible = amountsVisible) }
            item { RecentActivityCard(state.recentItems, amountsVisible, onOpenRecent) }
            item { PrimaryAccountsCard(state.accounts.take(2), amountsVisible, onOpenAccount) }"""
if old_order not in home:
    raise SystemExit("Home content order anchor changed")
home = home.replace(old_order, new_order, 1)
pattern = re.compile(
    r"@Composable\nprivate fun FinancialSnapshotCard\(.*?\n}\n\n@Composable\nprivate fun SnapshotMetric",
    re.S,
)
replacement = """@Composable
private fun FinancialSnapshotCard(
    state: HomeUiState,
    amountsVisible: Boolean,
    onOpenQuickEntry: () -> Unit,
) {
    MyFinHubHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md)) {
            MyFinHubHeroHeading(
                eyebrow = "Διαθέσιμα τώρα",
                title = "Τι μπορείς να χρησιμοποιήσεις",
                supporting = "Το καθαρό διαθέσιμο των ενεργών λογαριασμών σου",
            )
            MyFinHubHeroValue(if (amountsVisible) formatHomeEuro(state.liquidTotal) else "•••• €")
            MyFinHubHeroAction(
                label = "Νέα κίνηση",
                onClick = onOpenQuickEntry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MonthContextCard(state: HomeUiState, amountsVisible: Boolean) {
    val net = state.monthFlow.income - state.monthFlow.expense
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Ο μήνας μέχρι τώρα", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md)) {
                SnapshotMetric(
                    label = "Έσοδα",
                    value = if (amountsVisible) formatHomeEuro(state.monthFlow.income) else "•••• €",
                    tone = FinanceTone.Income,
                    modifier = Modifier.weight(1f),
                )
                SnapshotMetric(
                    label = "Έξοδα",
                    value = if (amountsVisible) formatHomeEuro(state.monthFlow.expense) else "•••• €",
                    tone = FinanceTone.Expense,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Καθαρή ροή", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MyFinHubAmountText(
                    text = if (amountsVisible) formatHomeEuro(net) else "•••• €",
                    tone = if (net >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun SnapshotMetric"""
home, n = pattern.subn(replacement, home, count=1)
if n != 1:
    raise SystemExit(f"FinancialSnapshotCard replacement count={n}")
home = home.replace(
    'Text("Λογαριασμοί", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)',
    'Text("Γρήγορη πρόσβαση", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)',
    1,
)
home_path.write_text(home)

# ACTIVITY — compact filtered-result context, no global KPI dashboard.
activity_path = Path("app/src/main/java/app/myfinhub/android/feature/activity/ActivityScreen.kt")
activity = activity_path.read_text()
pattern = re.compile(
    r"@Composable\nprivate fun ActivityProjectionSummary\(state: ActivityUiState\) \{.*?\n}\n\n@Composable\nprivate fun ActivitySummaryMetric",
    re.S,
)
replacement = """@Composable
private fun ActivityProjectionSummary(state: ActivityUiState) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Αποτελέσματα", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${state.visibleItems.size} ${if (state.visibleItems.size == 1) "κίνηση" else "κινήσεις"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MyFinHubAmountText(
                    text = formatSignedEuro(state.visibleNet),
                    tone = if (state.visibleNet >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.visiblePendingCount > 0) {
                Text(
                    text = "${state.visiblePendingCount} ${if (state.visiblePendingCount == 1) "κίνηση περιμένει" else "κινήσεις περιμένουν"} επιβεβαίωση από τον server",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivitySummaryMetric"""
activity, n = pattern.subn(replacement, activity, count=1)
if n != 1:
    raise SystemExit(f"Activity summary replacement count={n}")
activity_path.write_text(activity)

# MONEY — net position is primary; detailed values live only in their owning sections.
money_path = Path("app/src/main/java/app/myfinhub/android/feature/money/CanonicalMoneyScreens.kt")
money = money_path.read_text()
money = money.replace(
    'title = "Χρήματα",\n                subtitle = "Η συγχρονισμένη οικονομική σου εικόνα",',
    'title = "Περιουσία",\n                subtitle = "Τι έχεις και τι οφείλεις",',
    1,
)
hero_pattern = re.compile(
    r"            item \{\n                MyFinHubHeroCard\(modifier = Modifier\.fillMaxWidth\(\)\) \{.*?\n                \}\n            \}\n\n            item \{\n                MyFinHubSectionCard",
    re.S,
)
hero_replacement = """            item {
                MyFinHubHeroCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md)) {
                        MyFinHubHeroHeading(
                            eyebrow = "Καθαρή θέση",
                            title = "Η συνολική σου θέση",
                            supporting = "Λογαριασμοί και απαιτήσεις, μείον τις οφειλές",
                        )
                        MyFinHubHeroValue(formatCanonicalEuro(netPosition))
                        Text(
                            "Οι αναλυτικές αξίες εμφανίζονται στις αντίστοιχες ενότητες παρακάτω.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                        )
                    }
                }
            }

            item {
                MyFinHubSectionCard"""
money, n = hero_pattern.subn(hero_replacement, money, count=1)
if n != 1:
    raise SystemExit(f"Money hero replacement count={n}")
money_path.write_text(money)

# PLAN — the forecast value has exactly one visual owner.
plan_path = Path("app/src/main/java/app/myfinhub/android/feature/plan/CanonicalPlanScreens.kt")
plan = plan_path.read_text()
duplicate_forecast = """
            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        MyFinHubSectionHeading(
                            title = "Πρόβλεψη",
                            subtitle = "Μετά τις συγχρονισμένες επόμενες κινήσεις",
                            icon = MyFinHubIcons.Insights,
                            tone = FinanceTone.Transfer,
                        )
                        MyFinHubAmountText(
                            text = formatCanonicalPlanEuro(state.forecastEndBalance),
                            tone = if (state.forecastEndBalance >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            "Υπολογίζεται μόνο από τα τρέχοντα διαθέσιμα και τις καταγεγραμμένες επόμενες κινήσεις.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
"""
if plan.count(duplicate_forecast) != 1:
    raise SystemExit(f"Plan duplicate forecast count={plan.count(duplicate_forecast)}")
plan = plan.replace(duplicate_forecast, "\n", 1)
plan_path.write_text(plan)

# INSIGHTS — comparison/change, not a fourth current-month summary.
insights_path = Path("app/src/main/java/app/myfinhub/android/feature/insights/InsightsScreen.kt")
insights = insights_path.read_text()
replace_old = """    val latest = state.monthlyTrend.lastOrNull()
    val latestNet = latest?.let { it.income - it.expense } ?: 0.0
"""
replace_new = """    val latest = state.monthlyTrend.lastOrNull()
    val previous = state.monthlyTrend.dropLast(1).lastOrNull()
    val latestNet = latest?.let { it.income - it.expense } ?: 0.0
    val previousNet = previous?.let { it.income - it.expense }
"""
if replace_old not in insights:
    raise SystemExit("Insights state anchor changed")
insights = insights.replace(replace_old, replace_new, 1)
insights = insights.replace(
    'title = "Αναλύσεις",\n                subtitle = "Τι συμβαίνει στα χρήματά σου",',
    'title = "Εικόνα",\n                subtitle = "Τι αλλάζει και πού πηγαίνουν τα χρήματά σου",',
    1,
)
old_call = """                FinancialPulseCard(
                    latestMonth = latest?.label,
                    latestNet = latestNet,
                    averageSpend = state.averageMonthlySpend,
                    savingsRate = state.savingsRate,
                    largeFont = largeFont,
                )"""
new_call = """                FinancialPulseCard(
                    latestMonth = latest?.label,
                    latestExpense = latest?.expense ?: 0.0,
                    previousExpense = previous?.expense,
                    latestNet = latestNet,
                    previousNet = previousNet,
                    savingsRate = state.savingsRate,
                    largeFont = largeFont,
                )"""
if old_call not in insights:
    raise SystemExit("Insights pulse call anchor changed")
insights = insights.replace(old_call, new_call, 1)
pulse_pattern = re.compile(
    r"@Composable\nprivate fun FinancialPulseCard\(.*?\n}\n\n@Composable\nprivate fun PulseMetric",
    re.S,
)
pulse_replacement = """@Composable
private fun FinancialPulseCard(
    latestMonth: String?,
    latestExpense: Double,
    previousExpense: Double?,
    latestNet: Double,
    previousNet: Double?,
    savingsRate: Int,
    largeFont: Boolean,
) {
    val expenseDelta = previousExpense?.takeIf { kotlin.math.abs(it) > 0.005 }?.let { ((latestExpense - it) / kotlin.math.abs(it)) * 100.0 }
    val netDelta = previousNet?.let { latestNet - it }
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Τι άλλαξε", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            latestMonth?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (largeFont) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    InsightComparisonMetric("Έξοδα", formatEuro(latestExpense), expenseDelta?.let { "${if (it >= 0) "+" else ""}${it.toInt()}% από πριν" } ?: "Χωρίς βάση")
                    InsightComparisonMetric("Μεταβολή καθαρής ροής", netDelta?.let(::formatEuro) ?: "—", "έναντι προηγούμενου μήνα")
                    InsightComparisonMetric("Ρυθμός αποταμίευσης", "$savingsRate%", "τρέχων μήνας")
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    InsightComparisonMetric("Έξοδα", formatEuro(latestExpense), expenseDelta?.let { "${if (it >= 0) "+" else ""}${it.toInt()}%" } ?: "—", Modifier.weight(1f))
                    InsightComparisonMetric("Διαφορά ροής", netDelta?.let(::formatEuro) ?: "—", "από πριν", Modifier.weight(1f))
                }
                InsightComparisonMetric("Ρυθμός αποταμίευσης", "$savingsRate%", "τρέχων μήνας")
            }
        }
    }
}

@Composable
private fun InsightComparisonMetric(
    label: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PulseMetric"""
insights, n = pulse_pattern.subn(pulse_replacement, insights, count=1)
if n != 1:
    raise SystemExit(f"Insights pulse replacement count={n}")
insights_path.write_text(insights)

# SETTINGS — controls and safety information, not another dashboard hero.
settings_path = Path("app/src/main/java/app/myfinhub/android/feature/utilities/ProductionSettingsScreen.kt")
settings = settings_path.read_text()
settings = settings.replace(
    'subtitle = "Προσαρμογή και λογαριασμός",',
    'subtitle = "Εμφάνιση, απόρρητο, ενημέρωση και συνεδρία",',
    1,
)
settings_hero = re.compile(
    r"            MyFinHubHeroCard\(modifier = Modifier\.fillMaxWidth\(\)\) \{.*?\n            \}\n\n            MyFinHubSectionCard",
    re.S,
)
settings, n = settings_hero.subn("            MyFinHubSectionCard", settings, count=1)
if n != 1:
    raise SystemExit(f"Settings hero replacement count={n}")
settings_path.write_text(settings)
