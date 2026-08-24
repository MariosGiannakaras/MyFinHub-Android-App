package app.myfinhub.android.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "app.myfinhub.android"
private const val PRODUCT_ACTIVITY = "app.myfinhub.android.BenchmarkProductActivity"
private const val UI_TIMEOUT_MS = 10_000L
private const val UI_POLL_MS = 100L

private fun benchmarkProductIntent(): Intent = Intent(Intent.ACTION_MAIN).apply {
    component = ComponentName(TARGET_PACKAGE, PRODUCT_ACTIVITY)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}

private fun MacrobenchmarkScope.requireObject(
    message: String,
    finder: () -> UiObject2?,
): UiObject2 {
    repeat((UI_TIMEOUT_MS / UI_POLL_MS).toInt()) {
        finder()?.let { return it }
        Thread.sleep(UI_POLL_MS)
    }
    error(message)
}

private fun MacrobenchmarkScope.openActivityFromHome(context: String) {
    val navigationTarget = requireObject("$context did not expose the Activity navigation target.") {
        device.findObject(By.text("Κινήσεις")) ?: device.findObject(By.desc("Κινήσεις"))
    }
    navigationTarget.click()
    check(device.wait(Until.hasObject(By.text("Αναζήτηση κινήσεων")), UI_TIMEOUT_MS)) {
        "$context did not reach Activity."
    }
}

private fun MacrobenchmarkScope.openQuickEntryFromActivity(context: String) {
    // Compose may merge the Extended FAB icon and label into one accessibility node. Match the
    // spoken label as a substring rather than depending on a raw exact-text representation.
    val quickEntryAction = requireObject("$context did not expose Quick Entry from Activity.") {
        device.findObject(By.textContains("Νέα κίνηση")) ?:
            device.findObject(By.descContains("Νέα κίνηση"))
    }
    quickEntryAction.click()
    check(device.wait(Until.hasObject(By.text("Τι θέλεις να καταχωρίσεις;")), UI_TIMEOUT_MS)) {
        "$context did not reach the Quick Entry form."
    }
}

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun criticalJourneys() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait(benchmarkProductIntent())
        openActivityFromHome("Baseline Profile journey")
        checkNotNull(device.findObject(By.scrollable(true))) {
            "Baseline Profile journey did not expose the Activity scroll surface."
        }.fling(Direction.DOWN)
        openQuickEntryFromActivity("Baseline Profile journey")
    }
}

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.UseIfAvailable,
            warmupIterations = 1,
        ),
        startupMode = StartupMode.COLD,
        iterations = 3,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }
}

@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class CriticalJourneyBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val journeyMetrics = listOf(
        FrameTimingMetric(),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
    )

    @Test
    fun home() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = journeyMetrics,
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.UseIfAvailable,
            warmupIterations = 1,
        ),
        iterations = 3,
        setupBlock = {
            pressHome()
            startActivityAndWait(benchmarkProductIntent())
            check(device.wait(Until.hasObject(By.text("MyFinHub")), UI_TIMEOUT_MS)) {
                "Home benchmark did not reach the deterministic product host."
            }
        },
    ) {
        val scrollable = checkNotNull(device.findObject(By.scrollable(true))) {
            "Home benchmark did not expose a scrollable Home surface."
        }
        scrollable.fling(Direction.DOWN)
        scrollable.fling(Direction.UP)
    }

    @Test
    fun activity() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = journeyMetrics,
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.UseIfAvailable,
            warmupIterations = 1,
        ),
        iterations = 3,
        setupBlock = {
            pressHome()
            startActivityAndWait(benchmarkProductIntent())
        },
    ) {
        openActivityFromHome("Activity benchmark")
        val scrollable = checkNotNull(device.findObject(By.scrollable(true))) {
            "Activity benchmark did not expose the Activity scroll surface."
        }
        scrollable.fling(Direction.DOWN)
        scrollable.fling(Direction.UP)
    }

    @Test
    fun quickEntry() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = journeyMetrics,
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.UseIfAvailable,
            warmupIterations = 1,
        ),
        iterations = 3,
        setupBlock = {
            pressHome()
            startActivityAndWait(benchmarkProductIntent())
            openActivityFromHome("Quick Entry benchmark setup")
        },
    ) {
        openQuickEntryFromActivity("Quick Entry benchmark")
    }
}
