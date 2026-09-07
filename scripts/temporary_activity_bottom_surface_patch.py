from pathlib import Path

activity = Path('app/src/main/java/app/myfinhub/android/feature/activity/ActivityScreen.kt')
text = activity.read_text()
text = text.replace(
    'import androidx.compose.material3.Scaffold\n',
    'import androidx.compose.material3.Scaffold\nimport androidx.compose.material3.Surface\n',
    1,
)
old = '''        bottomBar = {\n            Row(\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .padding(\n                        horizontal = MyFinHubDesignMetrics.screenHorizontalPadding,\n                        vertical = MyFinHubSpacing.xs,\n                    ),\n                horizontalArrangement = Arrangement.End,\n            ) {\n                MyFinHubPrimaryAction(\n                    label = "Νέα κίνηση",\n                    onClick = onOpenQuickEntry,\n                    modifier = Modifier.semantics {\n                        contentDescription = "Δημιουργία νέας κίνησης"\n                    },\n                )\n            }\n        },'''
new = '''        bottomBar = {\n            Surface(color = MaterialTheme.colorScheme.background) {\n                Row(\n                    modifier = Modifier\n                        .fillMaxWidth()\n                        .padding(\n                            horizontal = MyFinHubDesignMetrics.screenHorizontalPadding,\n                            vertical = MyFinHubSpacing.xs,\n                        ),\n                    horizontalArrangement = Arrangement.End,\n                ) {\n                    MyFinHubPrimaryAction(\n                        label = "Νέα κίνηση",\n                        onClick = onOpenQuickEntry,\n                        modifier = Modifier.semantics {\n                            contentDescription = "Δημιουργία νέας κίνησης"\n                        },\n                    )\n                }\n            }\n        },'''
if old not in text:
    raise SystemExit('Activity bottomBar pattern not found')
activity.write_text(text.replace(old, new, 1))

app = Path('app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt')
text = app.read_text()
text = text.replace('import app.myfinhub.android.feature.activity.ActivityFilter\n', '', 1)
old = '''                        onOpenSupportingActivity = {\n                            onActivityAction(ActivityAction.FilterChanged(ActivityFilter.EXPENSE))\n                            activityBackStack.popToRoot()\n                            currentDestination = TopLevelDestination.ACTIVITY\n                        },'''
new = '''                        onOpenSupportingActivity = {\n                            activityBackStack.popToRoot()\n                            currentDestination = TopLevelDestination.ACTIVITY\n                        },'''
if old not in text:
    raise SystemExit('Insights to Activity navigation pattern not found')
app.write_text(text.replace(old, new, 1))

insights = Path('app/src/main/java/app/myfinhub/android/feature/insights/InsightsScreen.kt')
text = insights.read_text()
old = 'Text("Προβολή σχετικών κινήσεων")'
new = 'Text("Άνοιγμα κινήσεων")'
if old not in text:
    raise SystemExit('Insights activity action label not found')
insights.write_text(text.replace(old, new, 1))
