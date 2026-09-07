from pathlib import Path

path = Path("app/src/main/java/app/myfinhub/android/feature/activity/ActivityScreen.kt")
text = path.read_text()
old = '''        floatingActionButton = {
            MyFinHubPrimaryAction(
                label = "Νέα κίνηση",
                onClick = onOpenQuickEntry,
                modifier = Modifier.semantics {
                    contentDescription = "Δημιουργία νέας κίνησης"
                },
            )
        },'''
new = '''        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MyFinHubDesignMetrics.screenHorizontalPadding,
                        vertical = MyFinHubSpacing.xs,
                    ),
                horizontalArrangement = Arrangement.End,
            ) {
                MyFinHubPrimaryAction(
                    label = "Νέα κίνηση",
                    onClick = onOpenQuickEntry,
                    modifier = Modifier.semantics {
                        contentDescription = "Δημιουργία νέας κίνησης"
                    },
                )
            }
        },'''
if old not in text:
    raise SystemExit("Activity floating action pattern not found")
text = text.replace(old, new, 1)
old = '''                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = MyFinHubDesignMetrics.primaryActionMinHeight + MyFinHubSpacing.lg),'''
new = '''                    modifier = Modifier.fillMaxSize(),'''
if old not in text:
    raise SystemExit("Activity compact viewport padding pattern not found")
path.write_text(text.replace(old, new, 1))
