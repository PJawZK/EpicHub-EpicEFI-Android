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
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
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
            launchWarmTargetAndWaitForUi()
            warmAllDestinations()
            clickTarget(DASHBOARD_WORKSPACE, DASHBOARD_WORKSPACE_TEXT)
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
            launchWarmTargetAndWaitForUi()
            warmAllDestinations()
            clickTarget(DIAGNOSTICS_WORKSPACE, DIAGNOSTICS_WORKSPACE_TEXT)
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
        waitForAppUi()
        clickTarget(TUNER_WORKSPACE, TUNER_WORKSPACE_TEXT)
        clickTarget(TUNING_TOOLS_ROUTE, TUNING_TOOLS_ROUTE_TEXT)
        clickTarget(LOGGER_WORKSPACE, LOGGER_WORKSPACE_TEXT)
        clickTarget(ANALYZER_ROUTE, ANALYZER_ROUTE_TEXT)
        clickTarget(DIAGNOSTICS_WORKSPACE, DIAGNOSTICS_WORKSPACE_TEXT)
        clickDiagnosticsTab("Performance")
        clickTarget(DASHBOARD_WORKSPACE, DASHBOARD_WORKSPACE_TEXT)
    }

    /**
     * The two warm navigation benchmarks do not measure app startup. On the physical SM-A137F,
     * AndroidX startActivityAndWait() can fail its gfxinfo/framestats completion check when a fully
     * retained warm activity is resumed without producing a fresh frame. Launch with am start -W
     * instead, then establish readiness from the actual visible UI before setup continues.
     */
    private fun launchWarmTargetAndWaitForUi() {
        val output = device.executeShellCommand("am start -W -n $TARGET_COMPONENT")
        if (output.contains("Error", ignoreCase = true) || output.contains("Exception", ignoreCase = true)) {
            error("EpicHub warm launch failed: $output")
        }
        waitForAppUi()
    }

    private fun waitForAppUi() {
        waitForVisible(
            selectors = arrayOf(By.text(APP_TITLE), By.desc(DASHBOARD_WORKSPACE)),
            timeoutMs = APP_READY_TIMEOUT_MS,
        ) ?: error("EpicHub benchmark launched the target but could not observe its UI")
    }

    private fun warmAllDestinations() {
        clickTarget(TUNER_WORKSPACE, TUNER_WORKSPACE_TEXT)
        clickTarget(TUNING_TOOLS_ROUTE, TUNING_TOOLS_ROUTE_TEXT)
        clickTarget(TUNER_ROUTE, TUNER_ROUTE_TEXT)

        clickTarget(LOGGER_WORKSPACE, LOGGER_WORKSPACE_TEXT)
        clickTarget(ANALYZER_ROUTE, ANALYZER_ROUTE_TEXT)
        clickTarget(LOGGER_ROUTE, LOGGER_ROUTE_TEXT)

        clickTarget(DIAGNOSTICS_WORKSPACE, DIAGNOSTICS_WORKSPACE_TEXT)
        diagnosticsTabCycle()

        clickTarget(DASHBOARD_WORKSPACE, DASHBOARD_WORKSPACE_TEXT)
        settle(LONG_SETTLE_MS)
    }

    private fun workspaceAndLeafCycle() {
        clickTarget(TUNER_WORKSPACE, TUNER_WORKSPACE_TEXT)
        clickTarget(TUNING_TOOLS_ROUTE, TUNING_TOOLS_ROUTE_TEXT)
        clickTarget(TUNER_ROUTE, TUNER_ROUTE_TEXT)

        clickTarget(LOGGER_WORKSPACE, LOGGER_WORKSPACE_TEXT)
        clickTarget(ANALYZER_ROUTE, ANALYZER_ROUTE_TEXT)
        clickTarget(LOGGER_ROUTE, LOGGER_ROUTE_TEXT)

        clickTarget(DIAGNOSTICS_WORKSPACE, DIAGNOSTICS_WORKSPACE_TEXT)
        clickTarget(DASHBOARD_WORKSPACE, DASHBOARD_WORKSPACE_TEXT)
    }

    private fun diagnosticsTabCycle() {
        DIAGNOSTICS_TABS.forEach(::clickDiagnosticsTab)
    }

    /**
     * Prefer the stable accessibility description, but fall back to the visible label. The latter
     * keeps the external harness robust across Compose semantics merging differences on OEM builds
     * without adding benchmark-only state or code paths to EpicHub itself.
     */
    private fun clickTarget(description: String, fallbackText: String) {
        val target = waitForVisible(
            selectors = arrayOf(By.desc(description), By.text(fallbackText)),
            timeoutMs = FIND_TIMEOUT_MS,
        ) ?: error("EpicHub benchmark could not find UI target: $description / $fallbackText")

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

    private fun waitForVisible(selectors: Array<BySelector>, timeoutMs: Long): UiObject2? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        do {
            selectors.forEach { selector ->
                findVisible(selector)?.let { return it }
            }
            SystemClock.sleep(FIND_POLL_MS)
        } while (SystemClock.uptimeMillis() < deadline)
        return null
    }

    private fun findVisible(selector: BySelector): UiObject2? =
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
        const val TARGET_COMPONENT = "$TARGET_PACKAGE/.EpicHubActivity"
        const val APP_TITLE = "EpicHub"

        const val DASHBOARD_WORKSPACE = "Dashboard workspace"
        const val TUNER_WORKSPACE = "Tuner workspace"
        const val LOGGER_WORKSPACE = "Logger workspace"
        const val DIAGNOSTICS_WORKSPACE = "Diagnostics workspace"

        const val TUNER_ROUTE = "Tuner route"
        const val TUNING_TOOLS_ROUTE = "Tuning Tools route"
        const val LOGGER_ROUTE = "Logger route"
        const val ANALYZER_ROUTE = "Analyzer route"

        // Compact labels are identical on phone and tablet navigation presentations.
        const val DASHBOARD_WORKSPACE_TEXT = "Dash"
        const val TUNER_WORKSPACE_TEXT = "Tuner"
        const val LOGGER_WORKSPACE_TEXT = "Log"
        const val DIAGNOSTICS_WORKSPACE_TEXT = "Diag"
        const val TUNER_ROUTE_TEXT = "Tuner"
        const val TUNING_TOOLS_ROUTE_TEXT = "Tools"
        const val LOGGER_ROUTE_TEXT = "Logger"
        const val ANALYZER_ROUTE_TEXT = "Analyzer"

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

        const val FIND_TIMEOUT_MS = 10_000L
        const val APP_READY_TIMEOUT_MS = 15_000L
        const val FIND_POLL_MS = 50L
        const val CLICK_SETTLE_MS = 90L
        const val LONG_SETTLE_MS = 350L
        const val MAX_DIAGNOSTICS_REVEAL_ATTEMPTS = 4
        const val SWIPE_STEPS = 12
    }
}
