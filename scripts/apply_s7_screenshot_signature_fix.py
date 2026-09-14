from pathlib import Path

path = Path('app/src/screenshotTest/kotlin/app/myfinhub/android/feature/plan/S7PlanScreenshotTest.kt')
text = path.read_text()
old = 'CanonicalBudget2026Screen(s7PlanState(), {}, {})'
new = 'CanonicalBudget2026Screen(s7PlanState(), { _, _ -> }, {})'
count = text.count(old)
if count != 3:
    raise SystemExit(f'expected 3 budget screenshot calls, got {count}')
path.write_text(text.replace(old, new))
