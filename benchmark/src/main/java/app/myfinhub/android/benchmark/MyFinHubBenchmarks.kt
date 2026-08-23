package app.myfinhub.android.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.BaselineProfileRule
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkRule
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "app.myfinhub.android"
private const val PRODUCT_ACTIVITY = "app.myfinhub.android.BenchmarkProductActivity"
private const val UI_TIMEOUT_MS = 10_000L

private fun benchmarkProductIntent(): Intent = Intent(Intent.ACTION_MAIN).apply {
    component = ComponentName(TARGET_PACKAGE, PRODUCT_ACTIVITY)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        // Real production launcher/auth shell startup.
        pressHome()
        startActivityAndWait()

        // Deterministic representative product journeys, available only in profiling variants.
        pressHome()
        startActivityAndWait(benchmarkProductIntent())
        device.wait(Until.hasObject(By.text("Κινήσεις")), UI_TIMEOUT_MS)
        device.findObject(By.text("Κινήσεις"))?.click()
        device.wait(Until.hasObject(By.text("Αναζήτηση κινήσεων")), UI_TIMEOUT_MS)
        device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
        device.findObject(By.text("Νέα κίνηση"))?.click()
        device.wait(Until.hasObject(By.textContains("Ποσό")), UI_TIMEOUT_MS)
    }
}

@LargeTest
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
        iterations = 5,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }
}

@OptIn(ExperimentalMetricApi::class)
@LargeTest
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
            device.wait(Until.hasObject(By.text("MyFinHub")), UI_TIMEOUT_MS)
        },
    ) {
        device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
        device.findObject(By.scrollable(true))?.fling(Direction.UP)
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
            device.wait(Until.hasObject(By.text("Κινήσεις")), UI_TIMEOUT_MS)
        },
    ) {
        device.findObject(By.text("Κινήσεις"))?.click()
        device.wait(Until.hasObject(By.text("Αναζήτηση κινήσεων")), UI_TIMEOUT_MS)
        device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
        device.findObject(By.scrollable(true))?.fling(Direction.UP)
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
            device.wait(Until.hasObject(By.text("Κινήσεις")), UI_TIMEOUT_MS)
            device.findObject(By.text("Κινήσεις"))?.click()
            device.wait(Until.hasObject(By.text("Νέα κίνηση")), UI_TIMEOUT_MS)
        },
    ) {
        device.findObject(By.text("Νέα κίνηση"))?.click()
        device.wait(Until.hasObject(By.textContains("Ποσό")), UI_TIMEOUT_MS)
    }
}
