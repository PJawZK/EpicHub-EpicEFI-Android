package com.epicefi.epichub.benchmark

import android.graphics.Rect
import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * External, deterministic EpicHub performance journeys.
 *
 * The benchmark process drives the real performance APK through accessibility/UI Automator.
 * No benchmark loop runs inside EpicHub. The measured app therefore pays only its normal UI,
 * runtime and profiling costs.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class EpicHubNavigationBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * Measures already-warmed workspace and leaf-route switching without Diagnostics tab churn.
     * This is the primary signal for destination composition/reuse work.
     */
    @Test
    fun warmWorkspaceNavigation() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        iterations = WARM_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            warmAllDestinations()
            clickDescription(DASHBOARD_WORKSPACE)
            settle(LONG_SETTLE_MS)
        },
    ) {
        repeat(WORKSPACE_CYCLES_PER_ITERATION) {
            workspaceAndLeafCycle()
        }
    }

    /**
     * Measures Diagnostics presentation switching independently of primary workspace navigation.
     * All six tabs are warmed before the measured block.
     */
    @Test
    fun warmDiagnosticsTabs() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        iterations = WARM_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            warmAllDestinations()
            clickDescription(DIAGNOSTICS_WORKSPACE)
            clickDiagnosticsTab("Overview")
            settle(LONG_SETTLE_MS)
        },
    ) {
        repeat(DIAGNOSTICS_CYCLES_PER_ITERATION) {
            diagnosticsTabCycle()
        }
    }

    /**
     * Separates first-use/JIT/startup cost from warmed navigation. This intentionally uses no AOT
     * compilation so it represents a pessimistic fresh-install style path rather than the stable
     * full-compilation lab comparison above.
     */
    @Test
    fun coldFirstVisitJourney() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            StartupTimingMetric(),
            FrameTimingMetric(),
        ),
        compilationMode = CompilationMode.None(),
        iterations = COLD_ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
        clickDescription(TUNER_WORKSPACE)
        clickDescription(TUNING_TOOLS_ROUTE)
        clickDescription(LOGGER_WORKSPACE)
        clickDescription(ANALYZER_ROUTE)
        clickDescription(DIAGNOSTICS_WORKSPACE)
        clickDiagnosticsTab("Performance")
        clickDescription(DASHBOARD_WORKSPACE)
    }

    private fun warmAllDestinations() {
        clickDescription(TUNER_WORKSPACE)
        clickDescription(TUNING_TOOLS_ROUTE)
        clickDescription(TUNER_ROUTE)

        clickDescription(LOGGER_WORKSPACE)
        clickDescription(ANALYZER_ROUTE)
        clickDescription(LOGGER_ROUTE)

        clickDescription(DIAGNOSTICS_WORKSPACE)
        diagnosticsTabCycle()

        clickDescription(DASHBOARD_WORKSPACE)
        settle(LONG_SETTLE_MS)
    }

    private fun workspaceAndLeafCycle() {
        clickDescription(TUNER_WORKSPACE)
        clickDescription(TUNING_TOOLS_ROUTE)
        clickDescription(TUNER_ROUTE)

        clickDescription(LOGGER_WORKSPACE)
        clickDescription(ANALYZER_ROUTE)
        clickDescription(LOGGER_ROUTE)

        clickDescription(DIAGNOSTICS_WORKSPACE)
        clickDescription(DASHBOARD_WORKSPACE)
    }

    private fun diagnosticsTabCycle() {
        DIAGNOSTICS_TABS.forEach(::clickDiagnosticsTab)
    }

    private fun clickDescription(description: String) {
        val target = device.wait(
            Until.findObject(By.desc(description)),
            FIND_TIMEOUT_MS,
        ) ?: error("EpicHub benchmark could not find accessibility node: $description")

        target.click()
        settle(CLICK_SETTLE_MS)
    }

    private fun clickDiagnosticsTab(label: String) {
        repeat(MAX_DIAGNOSTICS_REVEAL_ATTEMPTS) { attempt ->
            findVisible(By.text(label))?.let { target ->
                target.click()
                settle(CLICK_SETTLE_MS)
                return
            }

            if (attempt == MAX_DIAGNOSTICS_REVEAL_ATTEMPTS - 1) {
                return@repeat
            }

            revealDiagnosticsTab(label)
        }
        error("EpicHub benchmark could not reveal Diagnostics tab: $label")
    }

    private fun revealDiagnosticsTab(label: String) {
        val visibleAnchor = DIAGNOSTICS_TABS
            .asSequence()
            .mapNotNull { findVisible(By.text(it)) }
            .firstOrNull()
            ?: error("No visible Diagnostics tab was available as a scroll anchor")

        val y = visibleAnchor.visibleBounds.centerY()
        val targetIndex = DIAGNOSTICS_TABS.indexOf(label)
        val moveTowardLaterTabs = targetIndex >= DIAGNOSTICS_TABS.size / 2
        val startX = if (moveTowardLaterTabs) device.displayWidth * 3 / 4 else device.displayWidth / 4
        val endX = if (moveTowardLaterTabs) device.displayWidth / 4 else device.displayWidth * 3 / 4

        device.swipe(startX, y, endX, y, SWIPE_STEPS)
        settle(CLICK_SETTLE_MS)
    }

    private fun findVisible(selector: androidx.test.uiautomator.BySelector): UiObject2? =
        device.findObjects(selector).firstOrNull { objectIsVisible(it.visibleBounds) }

    private fun objectIsVisible(bounds: Rect): Boolean =
        bounds.width() > 0 &&
            bounds.height() > 0 &&
            bounds.right > 0 &&
            bounds.bottom > 0 &&
            bounds.left < device.displayWidth &&
            bounds.top < device.displayHeight

    private fun settle(durationMs: Long) {
        SystemClock.sleep(durationMs)
    }

    private companion object {
        const val TARGET_PACKAGE = "com.epicefi.epichub"

        const val DASHBOARD_WORKSPACE = "Dashboard workspace"
        const val TUNER_WORKSPACE = "Tuner workspace"
        const val LOGGER_WORKSPACE = "Logger workspace"
        const val DIAGNOSTICS_WORKSPACE = "Diagnostics workspace"

        const val TUNER_ROUTE = "Tuner route"
        const val TUNING_TOOLS_ROUTE = "Tuning Tools route"
        const val LOGGER_ROUTE = "Logger route"
        const val ANALYZER_ROUTE = "Analyzer route"

        val DIAGNOSTICS_TABS = listOf(
            "Overview",
            "Performance",
            "Events",
            "Metrics",
            "Sessions",
            "Captures",
        )

        const val WARM_ITERATIONS = 5
        const val COLD_ITERATIONS = 5
        const val WORKSPACE_CYCLES_PER_ITERATION = 4
        const val DIAGNOSTICS_CYCLES_PER_ITERATION = 4

        const val FIND_TIMEOUT_MS = 3_000L
        const val CLICK_SETTLE_MS = 90L
        const val LONG_SETTLE_MS = 350L
        const val MAX_DIAGNOSTICS_REVEAL_ATTEMPTS = 4
        const val SWIPE_STEPS = 12
    }
}
