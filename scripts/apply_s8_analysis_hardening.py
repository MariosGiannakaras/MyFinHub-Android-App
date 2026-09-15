from pathlib import Path

ROOT = Path.cwd()


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, got {count}")
    target.write_text(text.replace(old, new, 1))


projection = "app/src/main/java/app/myfinhub/android/app/CanonicalInsightsProjection.kt"
replace_once(
    projection,
    '''    val previousMonth = currentMonth.minusMonths(1)\n    val comparisonDay = min(today.dayOfMonth, previousMonth.lengthOfMonth())\n    val previousMonthStart = previousMonth.atDay(1)\n    val previousMonthEnd = previousMonth.atDay(comparisonDay)''',
    '''    val previousMonth = currentMonth.minusMonths(1)\n    val (previousMonthStart, previousMonthEnd) = equivalentPreviousMonthWindow(today)''',
)

replace_once(
    projection,
    '''private fun insightsMonthLabel(month: YearMonth): String = month.atDay(1)\n''',
    '''/**\n * Previous comparison interval for month-to-date. The baseline keeps exactly the same\n * inclusive day count as the current month interval. When the previous calendar month\n * is shorter (for example 1–31 May versus April), the baseline extends backward across\n * the prior month boundary instead of silently using a shorter denominator or crashing.\n */\ninternal fun equivalentPreviousMonthWindow(today: LocalDate): Pair<LocalDate, LocalDate> {\n    val currentStart = YearMonth.from(today).atDay(1)\n    val inclusiveSpanMinusOne = ChronoUnit.DAYS.between(currentStart, today)\n    val previousMonth = YearMonth.from(today).minusMonths(1)\n    val previousEnd = previousMonth.atDay(min(today.dayOfMonth, previousMonth.lengthOfMonth()))\n    val previousStart = previousEnd.minusDays(inclusiveSpanMinusOne)\n    return previousStart to previousEnd\n}\n\nprivate fun insightsMonthLabel(month: YearMonth): String = month.atDay(1)\n''',
)

test_path = "app/src/test/java/app/myfinhub/android/app/CanonicalInsightsProjectionTest.kt"
replace_once(
    test_path,
    '''    @Test\n    fun thirtyDayComparison_hasExactlyEqualAdjacentWindowLength() {''',
    '''    @Test\n    fun monthEndAfterShorterPreviousMonth_keepsEquivalentInclusiveLength() {\n        val (start, end) = equivalentPreviousMonthWindow(LocalDate.of(2026, 5, 31))\n        assertEquals(LocalDate.of(2026, 3, 31), start)\n        assertEquals(LocalDate.of(2026, 4, 30), end)\n\n        val state = projectCanonicalInsightsState(categoryFixture(), LocalDate.of(2026, 5, 31))\n        val scope = state.periodScope(INSIGHTS_PERIOD_MONTH)\n        assertTrue(scope.contextLabel.contains("Πλήρης μήνας"))\n        assertTrue(scope.comparison.previousLabel.contains("31"))\n        assertTrue(scope.comparison.previousLabel.contains("30"))\n    }\n\n    @Test\n    fun thirtyDayComparison_hasExactlyEqualAdjacentWindowLength() {''',
)
