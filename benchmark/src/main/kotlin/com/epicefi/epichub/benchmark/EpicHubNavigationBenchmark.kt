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
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class EpicHubNavigationBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

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
            waitingFor = "initial EpicHub UI",
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

    private fun clickTarget(description: String, fallbackText: String) {
        val selectors = arrayOf(By.desc(description), By.text(fallbackText))
        if (!clickVisible(selectors, FIND_TIMEOUT_MS, "$description / $fallbackText")) {
            error("EpicHub benchmark could not find UI target: $description / $fallbackText")
        }
        settle(CLICK_SETTLE_MS)
        ensureTargetProcessAlive("after clicking $description / $fallbackText")
    }

    private fun clickDiagnosticsTab(label: String) {
        repeat(MAX_DIAGNOSTICS_REVEAL_ATTEMPTS) { attempt ->
            ensureTargetProcessAlive("while looking for Diagnostics tab $label")

            if (clickVisible(arrayOf(By.text(label)), DIAGNOSTICS_CLICK_TIMEOUT_MS, "Diagnostics tab $label")) {
                settle(CLICK_SETTLE_MS)
                ensureTargetProcessAlive("after clicking Diagnostics tab $label")
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
        val anchorBounds = DIAGNOSTICS_TABS
            .asSequence()
            .mapNotNull { findVisibleBounds(By.text(it)) }
            .firstOrNull()
            ?: error("No visible Diagnostics tab was available as a scroll anchor")

        val y = anchorBounds.centerY()
        val targetIndex = DIAGNOSTICS_TABS.indexOf(label)
        val moveTowardLaterTabs = targetIndex >= DIAGNOSTICS_TABS.size / 2
        val startX = if (moveTowardLaterTabs) device.displayWidth * 3 / 4 else device.displayWidth / 4
        val endX = if (moveTowardLaterTabs) device.displayWidth / 4 else device.displayWidth * 3 / 4

        device.swipe(startX, y, endX, y, SWIPE_STEPS)
        settle(CLICK_SETTLE_MS)
        ensureTargetProcessAlive("after revealing Diagnostics tab $label")
    }

    private fun clickVisible(
        selectors: Array<BySelector>,
        timeoutMs: Long,
        waitingFor: String,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        do {
            ensureTargetProcessAlive("while waiting for $waitingFor")
            selectors.forEach { selector ->
                val target = findVisible(selector) ?: return@forEach
                try {
                    target.click()
                    return true
                } catch (_: StaleObjectException) {
                    // Compose replaced this semantics node between lookup and click. Re-query.
                }
            }
            SystemClock.sleep(FIND_POLL_MS)
        } while (SystemClock.uptimeMillis() < deadline)
        return false
    }

    private fun waitForVisible(
        selectors: Array<BySelector>,
        timeoutMs: Long,
        waitingFor: String,
    ): UiObject2? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        do {
            ensureTargetProcessAlive("while waiting for $waitingFor")
            selectors.forEach { selector ->
                findVisible(selector)?.let { return it }
            }
            SystemClock.sleep(FIND_POLL_MS)
        } while (SystemClock.uptimeMillis() < deadline)
        return null
    }

    private fun findVisible(selector: BySelector): UiObject2? {
        device.findObjects(selector).forEach { candidate ->
            try {
                if (objectIsVisible(candidate.visibleBounds)) {
                    return candidate
                }
            } catch (_: StaleObjectException) {
                // Accessibility trees are transient during Compose recomposition; skip stale nodes.
            }
        }
        return null
    }

    private fun findVisibleBounds(selector: BySelector): Rect? {
        device.findObjects(selector).forEach { candidate ->
            try {
                val bounds = candidate.visibleBounds
                if (objectIsVisible(bounds)) {
                    return bounds
                }
            } catch (_: StaleObjectException) {
                // Re-query on the next candidate/poll rather than failing the benchmark harness.
            }
        }
        return null
    }

    private fun objectIsVisible(bounds: Rect): Boolean =
        bounds.width() > 0 &&
            bounds.height() > 0 &&
            bounds.right > 0 &&
            bounds.bottom > 0 &&
            bounds.left < device.displayWidth &&
            bounds.top < device.displayHeight

    private fun ensureTargetProcessAlive(context: String) {
        val pid = device.executeShellCommand("pidof $TARGET_PACKAGE").trim()
        if (pid.isEmpty()) {
            error("EpicHub process disappeared $context")
        }
    }

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
        const val DIAGNOSTICS_CLICK_TIMEOUT_MS = 500L
        const val APP_READY_TIMEOUT_MS = 15_000L
        const val FIND_POLL_MS = 50L
        const val CLICK_SETTLE_MS = 90L
        const val LONG_SETTLE_MS = 350L
        const val MAX_DIAGNOSTICS_REVEAL_ATTEMPTS = 4
        const val SWIPE_STEPS = 12
    }
}
