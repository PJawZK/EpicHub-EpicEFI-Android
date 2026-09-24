package com.epicefi.epichub.platform.diagnostics

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import com.epicefi.epichub.diagnostics.DiagnosticHub
import com.epicefi.epichub.diagnostics.DiagnosticMetricId

class UiFramePerformanceMonitor(
    private val diagnostics: DiagnosticHub,
) {
    private var handlerThread: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private var attachedWindow: Window? = null
    private var frameIndex: Long = 0
    private var fallbackBudgetMs: Double = DEFAULT_FRAME_BUDGET_MS

    fun start(window: Window) {
        if (listener != null) return

        fallbackBudgetMs = window.decorView.display?.refreshRate
            ?.takeIf { it.isFinite() && it > 0f }
            ?.let { 1_000.0 / it.toDouble() }
            ?: DEFAULT_FRAME_BUDGET_MS
        diagnostics.observeMetric(DiagnosticMetricId.UI_FRAME_BUDGET_MS, fallbackBudgetMs)

        val thread = HandlerThread("EpicHubFrameMetrics").apply { start() }
        val callback = Window.OnFrameMetricsAvailableListener { _, frameMetrics, droppedSinceLastInvocation ->
            recordFrame(frameMetrics, droppedSinceLastInvocation)
        }

        handlerThread = thread
        listener = callback
        attachedWindow = window
        window.addOnFrameMetricsAvailableListener(callback, Handler(thread.looper))
    }

    fun stop() {
        val callback = listener
        val window = attachedWindow
        if (callback != null && window != null) {
            window.removeOnFrameMetricsAvailableListener(callback)
        }
        listener = null
        attachedWindow = null
        handlerThread?.quitSafely()
        handlerThread = null
    }

    private fun recordFrame(
        frameMetrics: FrameMetrics,
        droppedSinceLastInvocation: Int,
    ) {
        val totalMs = frameMetrics.metricMs(FrameMetrics.TOTAL_DURATION) ?: return
        frameIndex += 1

        diagnostics.incrementMetric(DiagnosticMetricId.UI_FRAME_COUNT)
        diagnostics.observeMetric(DiagnosticMetricId.UI_FRAME_TOTAL_MS, totalMs)

        if (droppedSinceLastInvocation > 0) {
            diagnostics.incrementMetric(
                DiagnosticMetricId.UI_FRAME_METRICS_CALLBACK_DROPPED_COUNT,
                droppedSinceLastInvocation.toDouble(),
            )
        }

        val intendedVsync = frameMetrics.metricNanos(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
        val actualVsync = frameMetrics.metricNanos(FrameMetrics.VSYNC_TIMESTAMP)
        val vsyncDelayMs = if (intendedVsync != null && actualVsync != null) {
            (actualVsync - intendedVsync).coerceAtLeast(0L) / NANOS_PER_MILLISECOND
        } else {
            null
        }
        vsyncDelayMs?.let {
            diagnostics.observeMetric(DiagnosticMetricId.UI_FRAME_VSYNC_DELAY_MS, it)
        }

        val deadlineMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            frameMetrics.metricMs(FrameMetrics.DEADLINE)
        } else {
            null
        }
        val budgetMs = deadlineMs ?: fallbackBudgetMs

        if (frameIndex % BUDGET_SAMPLE_INTERVAL_FRAMES == 0L) {
            diagnostics.observeMetric(DiagnosticMetricId.UI_FRAME_BUDGET_MS, budgetMs)
            deadlineMs?.let {
                diagnostics.observeMetric(DiagnosticMetricId.UI_FRAME_DEADLINE_MS, it)
            }
        }

        val overrunMs = totalMs - budgetMs
        if (overrunMs <= 0.0) return

        val unknownDelayMs = frameMetrics.metricMs(FrameMetrics.UNKNOWN_DELAY_DURATION)
        val inputMs = frameMetrics.metricMs(FrameMetrics.INPUT_HANDLING_DURATION)
        val animationMs = frameMetrics.metricMs(FrameMetrics.ANIMATION_DURATION)
        val layoutMeasureMs = frameMetrics.metricMs(FrameMetrics.LAYOUT_MEASURE_DURATION)
        val drawMs = frameMetrics.metricMs(FrameMetrics.DRAW_DURATION)
        val syncMs = frameMetrics.metricMs(FrameMetrics.SYNC_DURATION)
        val commandIssueMs = frameMetrics.metricMs(FrameMetrics.COMMAND_ISSUE_DURATION)
        val swapBuffersMs = frameMetrics.metricMs(FrameMetrics.SWAP_BUFFERS_DURATION)
        val gpuMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            frameMetrics.metricMs(FrameMetrics.GPU_DURATION)
        } else {
            null
        }

        diagnostics.incrementMetric(DiagnosticMetricId.UI_JANK_FRAME_COUNT)
        diagnostics.observeMetric(DiagnosticMetricId.UI_FRAME_OVERRUN_MS, overrunMs)
        diagnostics.observeIfAvailable(DiagnosticMetricId.UI_JANK_UNKNOWN_DELAY_MS, unknownDelayMs)
        diagnostics.observeIfAvailable(DiagnosticMetricId.UI_JANK_INPUT_MS, inputMs)
        diagnostics.observeIfAvailable(DiagnosticMetricId.UI_JANK_ANIMATION_MS, animationMs)
        diagnostics.observeIfAvailable(DiagnosticMetricId.UI_JANK_LAYOUT_MEASURE_MS, layoutMeasureMs)
        diagnostics.observeIfAvailable(DiagnosticMetricId.UI_JANK_DRAW_MS, drawMs)
        diagnostics.observeIfAvailable(DiagnosticMetricId.UI_JANK_SYNC_MS, syncMs)
        diagnostics.observeIfAvailable(DiagnosticMetricId.UI_JANK_COMMAND_ISSUE_MS, commandIssueMs)
        diagnostics.observeIfAvailable(DiagnosticMetricId.UI_JANK_SWAP_BUFFERS_MS, swapBuffersMs)
        diagnostics.observeIfAvailable(DiagnosticMetricId.UI_JANK_GPU_MS, gpuMs)

        diagnostics.recordWorstFrame(
            frameNumber = frameIndex,
            totalMs = totalMs,
            budgetMs = budgetMs,
            overrunMs = overrunMs,
            vsyncDelayMs = vsyncDelayMs,
            unknownDelayMs = unknownDelayMs,
            animationMs = animationMs,
            layoutMeasureMs = layoutMeasureMs,
            drawMs = drawMs,
            syncMs = syncMs,
            commandIssueMs = commandIssueMs,
            swapBuffersMs = swapBuffersMs,
            gpuMs = gpuMs,
        )
    }

    private fun FrameMetrics.metricNanos(id: Int): Long? =
        getMetric(id).takeIf { it >= 0L }

    private fun FrameMetrics.metricMs(id: Int): Double? =
        metricNanos(id)?.div(NANOS_PER_MILLISECOND)

    private fun DiagnosticHub.observeIfAvailable(
        metric: DiagnosticMetricId,
        value: Double?,
    ) {
        value?.let { observeMetric(metric, it) }
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000.0
        const val DEFAULT_FRAME_BUDGET_MS = 1_000.0 / 60.0
        const val BUDGET_SAMPLE_INTERVAL_FRAMES = 30L
    }
}
