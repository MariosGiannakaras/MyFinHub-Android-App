from pathlib import Path

path = Path("app/src/main/java/app/myfinhub/android/feature/activity/ActivityScreen.kt")
text = path.read_text()
old = '''                ActivityList(
                    state = state,
                    onAction = onAction,
                    onSelect = onOpenDetail,
                    modifier = Modifier.fillMaxSize(),
                )'''
new = '''                ActivityList(
                    state = state,
                    onAction = onAction,
                    onSelect = onOpenDetail,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = MyFinHubDesignMetrics.primaryActionMinHeight + MyFinHubSpacing.lg),
                )'''
if old not in text:
    raise SystemExit("Compact ActivityList pattern not found")
path.write_text(text.replace(old, new, 1))
