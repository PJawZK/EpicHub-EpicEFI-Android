package com.epicefi.epichub.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.diagnostics.DiagnosticCapture
import com.epicefi.epichub.diagnostics.DiagnosticMetricId
import com.epicefi.epichub.diagnostics.DiagnosticMetricSummary
import com.epicefi.epichub.diagnostics.DiagnosticsSnapshot
import com.epicefi.epichub.ui.components.KeyValueRow
import com.epicefi.epichub.ui.components.StructuralCard
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    diagnostics: DiagnosticsSnapshot,
    onSaveCapture: (DiagnosticCapture) -> Unit,
    onShareCapture: (DiagnosticCapture) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Performance", "Events", "Metrics", "Sessions", "Captures")

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        ScrollableTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }

        when (selectedTab) {
            0 -> OverviewTab(diagnostics)
            1 -> PerformanceTab(diagnostics)
            2 -> EventsTab(diagnostics)
            3 -> MetricsTab(diagnostics)
            4 -> SessionsTab(diagnostics)
            else -> CapturesTab(diagnostics, onSaveCapture, onShareCapture)
        }
    }
}

@Composable
private fun OverviewTab(diagnostics: DiagnosticsSnapshot) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            StructuralCard(
                title = "Diagnostic recorder",
                modifier = Modifier.fillMaxWidth(),
            ) {
                KeyValueRow("Events retained", diagnostics.events.size.toString())
                KeyValueRow("Lifecycle owners", diagnostics.lifecycleOwners.size.toString())
                KeyValueRow("Saved problem captures", diagnostics.captures.size.toString())
                KeyValueRow(
                    "Problem-capture state",
                    diagnostics.activeCapture?.let { "${it.mode} #${it.captureId}" } ?: "idle",
                )
            }
        }
        item {
            StructuralCard(
                title = "Subsystem status",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "Evidence only — operational controls remain with their subsystem owners.",
            ) {
                KeyValueRow("App / UI", "active")
                KeyValueRow("Native engine / JNI", "active")
                KeyValueRow("Runtime", "synthetic")
                KeyValueRow("USB / ECU", "not attached")
                KeyValueRow("Logger / storage", "not attached")
                KeyValueRow("Tuning", "not attached")
            }
        }
    }
}

@Composable
private fun PerformanceTab(diagnostics: DiagnosticsSnapshot) {
    val frames = diagnostics.metric(DiagnosticMetricId.UI_FRAME_COUNT)?.current ?: 0.0
    val jankFrames = diagnostics.metric(DiagnosticMetricId.UI_JANK_FRAME_COUNT)?.current ?: 0.0
    val jankPercent = if (frames > 0.0) jankFrames / frames * 100.0 else 0.0
    val frameTotal = diagnostics.metric(DiagnosticMetricId.UI_FRAME_TOTAL_MS)
    val frameBudget = diagnostics.metric(DiagnosticMetricId.UI_FRAME_BUDGET_MS)
    val overrun = diagnostics.metric(DiagnosticMetricId.UI_FRAME_OVERRUN_MS)
    val vsyncDelay = diagnostics.metric(DiagnosticMetricId.UI_FRAME_VSYNC_DELAY_MS)
    val cpu = diagnostics.metric(DiagnosticMetricId.PROCESS_CPU_PERCENT)
    val javaHeap = diagnostics.metric(DiagnosticMetricId.PROCESS_JAVA_HEAP_USED_MB)
    val nativeHeap = diagnostics.metric(DiagnosticMetricId.PROCESS_NATIVE_HEAP_ALLOCATED_MB)
    val pss = diagnostics.metric(DiagnosticMetricId.PROCESS_PSS_MB)
    val allocated = diagnostics.metric(DiagnosticMetricId.PROCESS_ALLOCATED_MB)
    val gcCount = diagnostics.metric(DiagnosticMetricId.PROCESS_GC_COUNT)
    val gcTime = diagnostics.metric(DiagnosticMetricId.PROCESS_GC_TIME_MS)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            StructuralCard(
                title = "Performance recording",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "For a clean test: Problem capture → Start reproduction capture, reproduce the slowdown, then Stop and Save ZIP. Capture metrics are scoped to that reproduction window.",
            ) {
                KeyValueRow("Frames measured", frames.toLong().toString())
                KeyValueRow("Jank frames", "${jankFrames.toLong()} (${jankPercent.formatMetric()}%)")
                KeyValueRow("Frame budget", frameBudget?.current?.formatWithUnit("ms") ?: "waiting")
                KeyValueRow("Frame p50 / p95 / p99", frameTotal.percentiles("ms"))
                KeyValueRow("Frame samples total / retained", frameTotal.sampleWindow())
                KeyValueRow("Jank overrun p95 / p99", overrun.tailPercentiles("ms"))
                KeyValueRow("UI-thread vsync delay p95 / p99", vsyncDelay.tailPercentiles("ms"))
            }
        }
        item {
            StructuralCard(
                title = "Process load",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "CPU is process CPU time expressed as percent of one fully occupied core. Memory rows show first → current and capture/session delta.",
            ) {
                KeyValueRow("CPU p50 / p95 / p99", cpu.percentiles("%"))
                KeyValueRow("Java heap", javaHeap.trend("MiB"))
                KeyValueRow("Native heap", nativeHeap.trend("MiB"))
                KeyValueRow("PSS", pss.trend("MiB"))
                KeyValueRow("Allocated bytes", allocated.trend("MiB"))
                KeyValueRow("GC count", gcCount.delta("count"))
                KeyValueRow("GC time", gcTime.delta("ms"))
            }
        }
        item {
            StructuralCard(
                title = "Jank breakdown",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "These timings are sampled only for frames that miss their frame budget/deadline.",
            ) {
                JankMetricRow(diagnostics, "Unknown/UI-thread delay", DiagnosticMetricId.UI_JANK_UNKNOWN_DELAY_MS)
                JankMetricRow(diagnostics, "Input handling", DiagnosticMetricId.UI_JANK_INPUT_MS)
                JankMetricRow(diagnostics, "Animation", DiagnosticMetricId.UI_JANK_ANIMATION_MS)
                JankMetricRow(diagnostics, "Layout / measure", DiagnosticMetricId.UI_JANK_LAYOUT_MEASURE_MS)
                JankMetricRow(diagnostics, "Draw", DiagnosticMetricId.UI_JANK_DRAW_MS)
                JankMetricRow(diagnostics, "Render sync", DiagnosticMetricId.UI_JANK_SYNC_MS)
                JankMetricRow(diagnostics, "GPU command issue", DiagnosticMetricId.UI_JANK_COMMAND_ISSUE_MS)
                JankMetricRow(diagnostics, "Swap buffers", DiagnosticMetricId.UI_JANK_SWAP_BUFFERS_MS)
                JankMetricRow(diagnostics, "GPU duration", DiagnosticMetricId.UI_JANK_GPU_MS)
            }
        }
    }
}

@Composable
private fun JankMetricRow(
    diagnostics: DiagnosticsSnapshot,
    label: String,
    metricId: DiagnosticMetricId,
) {
    KeyValueRow(label, diagnostics.metric(metricId).tailPercentiles("ms"))
}

@Composable
private fun EventsTab(diagnostics: DiagnosticsSnapshot) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = diagnostics.events.asReversed(),
            key = { it.eventId },
        ) { event ->
            StructuralCard(
                title = event.code.stableId,
                modifier = Modifier.fillMaxWidth(),
                subtitle = "${event.severity} · ${event.domain}",
            ) {
                event.correlationId?.let { KeyValueRow("Correlation", it) }
                event.sessionId?.let { KeyValueRow("Session", it.toString()) }
                KeyValueRow("Event ID", event.eventId.toString())
            }
        }
    }
}

@Composable
private fun MetricsTab(diagnostics: DiagnosticsSnapshot) {
    val observedMetrics = diagnostics.metrics.filter { it.sampleCount > 0 }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = observedMetrics,
            key = { it.metric.stableId },
        ) { metric ->
            StructuralCard(
                title = metric.metric.stableId,
                modifier = Modifier.fillMaxWidth(),
                subtitle = metric.metric.unit,
            ) {
                KeyValueRow("First / Current", "${metric.first.formatMetric()} / ${metric.current.formatMetric()}")
                KeyValueRow("Delta", (metric.current - metric.first).formatMetric())
                KeyValueRow("Min / Max", "${metric.minimum.formatMetric()} / ${metric.maximum.formatMetric()}")
                KeyValueRow("p50 / p95 / p99", "${metric.p50.formatMetric()} / ${metric.p95.formatMetric()} / ${metric.p99.formatMetric()}")
                KeyValueRow("Samples total / retained", "${metric.sampleCount} / ${metric.retainedSampleCount}")
            }
        }
    }
}

@Composable
private fun SessionsTab(diagnostics: DiagnosticsSnapshot) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = diagnostics.lifecycleOwners,
            key = { it.ownerId },
        ) { owner ->
            StructuralCard(
                title = owner.ownerKind,
                modifier = Modifier.fillMaxWidth(),
                subtitle = owner.ownerId,
            ) {
                KeyValueRow("State", owner.currentState)
                KeyValueRow("Generation", owner.generation.toString())
                KeyValueRow("Late events", owner.lateEventCount.toString())
            }
        }
    }
}

@Composable
private fun CapturesTab(
    diagnostics: DiagnosticsSnapshot,
    onSaveCapture: (DiagnosticCapture) -> Unit,
    onShareCapture: (DiagnosticCapture) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (diagnostics.captures.isEmpty()) {
            item {
                Text(
                    "No finalized problem captures yet. Use the global Problem capture control while reproducing or immediately after noticing a problem.",
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(
            items = diagnostics.captures,
            key = { it.captureId },
        ) { capture ->
            val frames = capture.metric(DiagnosticMetricId.UI_FRAME_COUNT)?.current ?: 0.0
            val jankFrames = capture.metric(DiagnosticMetricId.UI_JANK_FRAME_COUNT)?.current ?: 0.0
            val jankPercent = if (frames > 0.0) jankFrames / frames * 100.0 else 0.0
            val worstFrame = capture.worstFrames.firstOrNull()
            StructuralCard(
                title = "Problem capture #${capture.captureId}",
                modifier = Modifier.fillMaxWidth(),
                subtitle = DateFormat.getDateTimeInstance().format(Date(capture.createdAtWallClockMillis)),
            ) {
                KeyValueRow("Mode", capture.mode.name)
                KeyValueRow("Events", capture.events.size.toString())
                KeyValueRow("Markers", capture.markers.size.toString())
                if (frames > 0.0) {
                    KeyValueRow("Performance frames", frames.toLong().toString())
                    KeyValueRow("Jank", "${jankFrames.toLong()} (${jankPercent.formatMetric()}%)")
                }
                if (worstFrame != null) {
                    KeyValueRow(
                        "Worst frame",
                        "${worstFrame.destinationId} · +${worstFrame.overrunMs.formatMetric()} ms",
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSaveCapture(capture) }) {
                        Text("Save ZIP")
                    }
                    TextButton(onClick = { onShareCapture(capture) }) {
                        Text("Share ZIP")
                    }
                }
            }
        }
    }
}

private fun DiagnosticsSnapshot.metric(id: DiagnosticMetricId): DiagnosticMetricSummary? =
    metrics.firstOrNull { it.metric == id && it.sampleCount > 0 }

private fun DiagnosticCapture.metric(id: DiagnosticMetricId): DiagnosticMetricSummary? =
    metrics.firstOrNull { it.metric == id && it.sampleCount > 0 }

private fun DiagnosticMetricSummary?.percentiles(unit: String): String =
    this?.let { "${p50.formatMetric()} / ${p95.formatMetric()} / ${p99.formatMetric()} $unit" } ?: "waiting"

private fun DiagnosticMetricSummary?.tailPercentiles(unit: String): String =
    this?.let { "${p95.formatMetric()} / ${p99.formatMetric()} $unit" } ?: "none observed"

private fun DiagnosticMetricSummary?.sampleWindow(): String =
    this?.let { "$sampleCount / $retainedSampleCount" } ?: "waiting"

private fun DiagnosticMetricSummary?.trend(unit: String): String =
    this?.let {
        "${first.formatMetric()} → ${current.formatMetric()} " +
            "(Δ ${(current - first).formatSignedMetric()}) $unit; max ${maximum.formatMetric()}"
    } ?: "waiting"

private fun DiagnosticMetricSummary?.delta(unit: String): String =
    this?.let { "Δ ${(current - first).formatSignedMetric()} $unit; current ${current.formatMetric()}" } ?: "waiting"

private fun Double.formatWithUnit(unit: String): String = "${formatMetric()} $unit"

private fun Double.formatMetric(): String = String.format(Locale.US, "%.3f", this)

private fun Double.formatSignedMetric(): String = String.format(Locale.US, "%+.3f", this)
