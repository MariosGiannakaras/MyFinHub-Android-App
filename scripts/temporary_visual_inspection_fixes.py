from pathlib import Path

home = Path("app/src/main/java/app/myfinhub/android/feature/home/ProductionHomeScreen.kt")
text = home.read_text()
old = '''            MyFinHubScreenHeader(
                title = "MyFinHub",
                subtitle = "Οι λογαριασμοί μου",
                navigation = { MyFinHubBrandMark() },
                trailing = { TextButton(onClick = onOpenSettings) { Text("Ρυθμίσεις") } },
            )'''
new = '''            MyFinHubScreenHeader(
                title = "MyFinHub",
                navigation = { MyFinHubBrandMark() },
                trailing = { TextButton(onClick = onOpenSettings) { Text("Ρυθμίσεις") } },
            )'''
if old not in text:
    raise SystemExit("Home header pattern not found")
text = text.replace(old, new, 1)

old = '''        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Κύριοι λογαριασμοί", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Οι 3 λογαριασμοί που χρησιμοποιείς περισσότερο",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenQuickEntry) { Text("Νέα κίνηση") }
        }'''
new = '''        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
            Text("Κύριοι λογαριασμοί", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Οι 3 βασικοί λογαριασμοί",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenQuickEntry) { Text("Νέα κίνηση") }
        }'''
if old not in text:
    raise SystemExit("Primary account heading pattern not found")
text = text.replace(old, new, 1)
home.write_text(text)

activity = Path("app/src/main/java/app/myfinhub/android/feature/activity/ActivityScreen.kt")
text = activity.read_text()
old = "bottom = MyFinHubDesignMetrics.navigationContentBottomClearance,"
new = "bottom = MyFinHubDesignMetrics.productSnackbarBottomClearance,"
if old not in text:
    raise SystemExit("Activity bottom-clearance pattern not found")
activity.write_text(text.replace(old, new, 1))
