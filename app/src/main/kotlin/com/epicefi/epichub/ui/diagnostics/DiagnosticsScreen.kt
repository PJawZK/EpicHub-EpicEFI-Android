package com.epicefi.epichub.ui.diagnostics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.diagnostics.DiagnosticCapture
import com.epicefi.epichub.diagnostics.DiagnosticMetricId
import com.epicefi.epichub.diagnostics.DiagnosticMetricSummary
import com.epicefi.epichub.diagnostics.DiagnosticsSnapshot
import com.epicefi.epichub.ui.components.KeyValueRow
import com.epicefi.epichub.ui.components.StaticSelectionChoice
import com.epicefi.epichub.ui.components.StructuralCard
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DiagnosticsTab(
    val title: String,
    val contextId: String,
) {
    OVERVIEW("Overview", "diagnostics/overview"),
    PERFORMANCE("Performance", "diagnostics/performance"),
    EVENTS("Events", "diagnostics/events"),
    METRICS("Metrics", "diagnostics/metrics"),
    SESSIONS("Sessions", "diagnostics/sessions"),
    CAPTURES("Captures", "diagnostics/captures"),
}

private data class OverviewPresentation(
    val eventCount: Int,
    val lifecycleOwnerCount: Int,
    val captureCount: Int,
    val activeCapture: String,
)

private data class PerformancePresentation(
    val framesMeasured: String,
    val jankFrames: String,
    val firstDrawJank: String,
    val transitionJank: String,
    val steadyJank: String,
    val callbackDrops: String,
    val frameBudget: String,
    val framePercentiles: String,
    val frameSamples: String,
    val overrunPercentiles: String,
    val vsyncDelayPercentiles: String,
    val cpuPercentiles: String,
    val javaHeap: String,
    val nativeHeap: String,
    val pss: String,
    val allocated: String,
    val gcCount: String,
    val gcTime: String,
    val projectionBuild: String,
    val projectionAllocated: String,
    val projectionCount: String,
    val unknownDelay: String,
    val input: String,
    val animation: String,
    val layoutMeasure: String,
    val draw: String,
    val sync: String,
    val commandIssue: String,
    val swapBuffers: String,
    val gpu: String,
)

private data class MetricPresentation(
    val stableId: String,
    val unit: String,
    val firstCurrent: String,
    val delta: String,
    val minMax: String,
    val percentiles: String,
    val samples: String,
)

private val emptyPerformancePresentation = PerformancePresentation(
    framesMeasured = "waiting",
    jankFrames = "waiting",
    firstDrawJank = "waiting",
    transitionJank = "waiting",
    steadyJank = "waiting",
    callbackDrops = "waiting",
    frameBudget = "waiting",
    framePercentiles = "waiting",
    frameSamples = "waiting",
    overrunPercentiles = "waiting",
    vsyncDelayPercentiles = "waiting",
    cpuPercentiles = "waiting",
    javaHeap = "waiting",
    nativeHeap = "waiting",
    pss = "waiting",
    allocated = "waiting",
    gcCount = "waiting",
    gcTime = "waiting",
    projectionBuild = "waiting",
    projectionAllocated = "waiting",
    projectionCount = "waiting",
    unknownDelay = "waiting",
    input = "waiting",
    animation = "waiting",
    layoutMeasure = "waiting",
    draw = "waiting",
    sync = "waiting",
    commandIssue = "waiting",
    swapBuffers = "waiting",
    gpu = "waiting",
)

@Composable
fun DiagnosticsScreen(
    diagnostics: State<DiagnosticsSnapshot>,
    onTabSelected: (DiagnosticsTab) -> Unit,
    onSaveCapture: (DiagnosticCapture) -> Unit,
    onShareCapture: (DiagnosticCapture) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = DiagnosticsTab.entries[selectedTabIndex]

    LaunchedEffect(selectedTab) {
        onTabSelected(selectedTab)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        DiagnosticsTabStrip(
            selectedTabIndex = selectedTabIndex,
            onSelected = { selectedTabIndex = it },
        )

        when (selectedTab) {
            DiagnosticsTab.OVERVIEW -> OverviewTab(diagnostics)
            DiagnosticsTab.PERFORMANCE -> PerformanceTab(diagnostics)
            DiagnosticsTab.EVENTS -> EventsTab(diagnostics)
            DiagnosticsTab.METRICS -> MetricsTab(diagnostics)
            DiagnosticsTab.SESSIONS -> SessionsTab(diagnostics)
            DiagnosticsTab.CAPTURES -> CapturesTab(diagnostics, onSaveCapture, onShareCapture)
        }
    }
}

@Composable
private fun DiagnosticsTabStrip(
    selectedTabIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DiagnosticsTab.entries.forEachIndexed { index, tab ->
                StaticSelectionChoice(
                    label = tab.title,
                    selected = selectedTabIndex == index,
                    onClick = { onSelected(index) },
                )
            }
        }
    }
}

@Composable
private fun OverviewTab(diagnostics: State<DiagnosticsSnapshot>) {
    val presentation by remember(diagnostics) {
        derivedStateOf {
            val snapshot = diagnostics.value
            OverviewPresentation(
                eventCount = snapshot.eventCount,
                lifecycleOwnerCount = snapshot.lifecycleOwnerCount,
                captureCount = snapshot.captureCount,
                activeCapture = snapshot.activeCapture?.let { "${it.mode} #${it.captureId}" } ?: "idle",
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "recorder") {
            StructuralCard(
                title = "Diagnostic recorder",
                modifier = Modifier.fillMaxWidth(),
            ) {
                KeyValueRow("Events retained", presentation.eventCount.toString())
                KeyValueRow("Lifecycle owners", presentation.lifecycleOwnerCount.toString())
                KeyValueRow("Saved problem captures", presentation.captureCount.toString())
                KeyValueRow("Problem-capture state", presentation.activeCapture)
            }
        }
        item(key = "subsystem-status") {
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
private fun PerformanceTab(diagnostics: State<DiagnosticsSnapshot>) {
    val metrics = diagnostics.value.metrics
    val presentation by produceState(
        initialValue = emptyPerformancePresentation,
        key1 = metrics,
    ) {
        value = withContext(Dispatchers.Default) {
            buildPerformancePresentation(metrics)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "performance-frames") {
            StructuralCard(
                title = "Performance recording",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "Capture-local frame evidence stays independent of this presentation refresh.",
            ) {
                KeyValueRow("Frames measured", presentation.framesMeasured)
                KeyValueRow("Jank frames", presentation.jankFrames)
                KeyValueRow("First-draw jank", presentation.firstDrawJank)
                KeyValueRow("Jank ≤500 ms after route/tab change", presentation.transitionJank)
                KeyValueRow("Steady-state jank", presentation.steadyJank)
                KeyValueRow("Frame callback drops", presentation.callbackDrops)
                KeyValueRow("Frame budget", presentation.frameBudget)
                KeyValueRow("Frame p50 / p95 / p99", presentation.framePercentiles)
                KeyValueRow("Frame samples total / retained", presentation.frameSamples)
                KeyValueRow("Jank overrun p95 / p99", presentation.overrunPercentiles)
                KeyValueRow("UI-thread vsync delay p95 / p99", presentation.vsyncDelayPercentiles)
            }
        }
        item(key = "performance-process") {
            StructuralCard(
                title = "Process load",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "CPU is process CPU time as percent of one fully occupied core. Memory rows show first → current and delta.",
            ) {
                KeyValueRow("CPU p50 / p95 / p99", presentation.cpuPercentiles)
                KeyValueRow("Java heap", presentation.javaHeap)
                KeyValueRow("Native heap", presentation.nativeHeap)
                KeyValueRow("PSS", presentation.pss)
                KeyValueRow("Allocated bytes", presentation.allocated)
                KeyValueRow("GC count", presentation.gcCount)
                KeyValueRow("GC time", presentation.gcTime)
            }
        }
        item(key = "performance-diagnostics") {
            StructuralCard(
                title = "Diagnostics UI cost",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "Projection work is measured off the UI thread.",
            ) {
                KeyValueRow("Projection build p50 / p95 / p99", presentation.projectionBuild)
                KeyValueRow("Projection allocation p50 / p95 / p99", presentation.projectionAllocated)
                KeyValueRow("Projection count", presentation.projectionCount)
            }
        }
        item(key = "performance-jank") {
            StructuralCard(
                title = "Jank breakdown",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "These timings are sampled only for frames that miss their frame deadline.",
            ) {
                KeyValueRow("Unknown/UI-thread delay", presentation.unknownDelay)
                KeyValueRow("Input handling", presentation.input)
                KeyValueRow("Animation", presentation.animation)
                KeyValueRow("Layout / measure", presentation.layoutMeasure)
                KeyValueRow("Draw", presentation.draw)
                KeyValueRow("Render sync", presentation.sync)
                KeyValueRow("GPU command issue", presentation.commandIssue)
                KeyValueRow("Swap buffers", presentation.swapBuffers)
                KeyValueRow("GPU duration", presentation.gpu)
            }
        }
    }
}

@Composable
private fun EventsTab(diagnostics: State<DiagnosticsSnapshot>) {
    val events by remember(diagnostics) {
        derivedStateOf { diagnostics.value.events }
    }
    val newestFirst = remember(events) { events.asReversed() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = newestFirst,
            key = { it.eventId },
            contentType = { "diagnostic-event" },
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
private fun MetricsTab(diagnostics: State<DiagnosticsSnapshot>) {
    val metrics by remember(diagnostics) {
        derivedStateOf { diagnostics.value.metrics }
    }
    val presentations by produceState(
        initialValue = emptyList<MetricPresentation>(),
        key1 = metrics,
    ) {
        value = withContext(Dispatchers.Default) {
            buildMetricPresentations(metrics)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = presentations,
            key = { it.stableId },
            contentType = { "diagnostic-metric" },
        ) { metric ->
            StructuralCard(
                title = metric.stableId,
                modifier = Modifier.fillMaxWidth(),
                subtitle = metric.unit,
            ) {
                KeyValueRow("First / Current", metric.firstCurrent)
                KeyValueRow("Delta", metric.delta)
                KeyValueRow("Min / Max", metric.minMax)
                KeyValueRow("p50 / p95 / p99", metric.percentiles)
                KeyValueRow("Samples total / retained", metric.samples)
            }
        }
    }
}

@Composable
private fun SessionsTab(diagnostics: State<DiagnosticsSnapshot>) {
    val lifecycleOwners by remember(diagnostics) {
        derivedStateOf { diagnostics.value.lifecycleOwners }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = lifecycleOwners,
            key = { it.ownerId },
            contentType = { "lifecycle-owner" },
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
    diagnostics: State<DiagnosticsSnapshot>,
    onSaveCapture: (DiagnosticCapture) -> Unit,
    onShareCapture: (DiagnosticCapture) -> Unit,
) {
    val captures by remember(diagnostics) {
        derivedStateOf { diagnostics.value.captures }
    }
    val dateFormat = remember { DateFormat.getDateTimeInstance() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (captures.isEmpty()) {
            item(key = "empty-captures") {
                Text(
                    "No finalized problem captures yet. Use the global Problem capture control while reproducing or immediately after noticing a problem.",
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(
            items = captures,
            key = { it.captureId },
            contentType = { "diagnostic-capture" },
        ) { capture ->
            val metricIndex = remember(capture.metrics) {
                capture.metrics.associateBy { it.metric }
            }
            val frames = metricIndex[DiagnosticMetricId.UI_FRAME_COUNT]?.takeIf { it.sampleCount > 0 }?.current ?: 0.0
            val jankFrames = metricIndex[DiagnosticMetricId.UI_JANK_FRAME_COUNT]?.takeIf { it.sampleCount > 0 }?.current ?: 0.0
            val jankPercent = if (frames > 0.0) jankFrames / frames * 100.0 else 0.0
            val worstFrame = capture.worstFrames.firstOrNull()
            val createdAt = remember(capture.createdAtWallClockMillis) {
                dateFormat.format(Date(capture.createdAtWallClockMillis))
            }
            StructuralCard(
                title = "Problem capture #${capture.captureId}",
                modifier = Modifier.fillMaxWidth(),
                subtitle = createdAt,
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

private fun buildMetricPresentations(metrics: List<DiagnosticMetricSummary>): List<MetricPresentation> =
    metrics.asSequence()
        .filter { it.sampleCount > 0 }
        .map { metric ->
            MetricPresentation(
                stableId = metric.metric.stableId,
                unit = metric.metric.unit,
                firstCurrent = "${metric.first.formatMetric()} / ${metric.current.formatMetric()}",
                delta = (metric.current - metric.first).formatMetric(),
                minMax = "${metric.minimum.formatMetric()} / ${metric.maximum.formatMetric()}",
                percentiles = "${metric.p50.formatMetric()} / ${metric.p95.formatMetric()} / ${metric.p99.formatMetric()}",
                samples = "${metric.sampleCount} / ${metric.retainedSampleCount}",
            )
        }
        .toList()

private fun buildPerformancePresentation(metrics: List<DiagnosticMetricSummary>): PerformancePresentation {
    val index = metrics.asSequence()
        .filter { it.sampleCount > 0 }
        .associateBy { it.metric }

    fun metric(id: DiagnosticMetricId): DiagnosticMetricSummary? = index[id]
    fun count(id: DiagnosticMetricId): String = metric(id)?.current?.toLong()?.toString() ?: "0"

    val frames = metric(DiagnosticMetricId.UI_FRAME_COUNT)?.current ?: 0.0
    val jankFrames = metric(DiagnosticMetricId.UI_JANK_FRAME_COUNT)?.current ?: 0.0
    val jankPercent = if (frames > 0.0) jankFrames / frames * 100.0 else 0.0
    val frameTotal = metric(DiagnosticMetricId.UI_FRAME_TOTAL_MS)
    val frameBudget = metric(DiagnosticMetricId.UI_FRAME_BUDGET_MS)
    val overrun = metric(DiagnosticMetricId.UI_FRAME_OVERRUN_MS)
    val vsyncDelay = metric(DiagnosticMetricId.UI_FRAME_VSYNC_DELAY_MS)
    val cpu = metric(DiagnosticMetricId.PROCESS_CPU_PERCENT)
    val javaHeap = metric(DiagnosticMetricId.PROCESS_JAVA_HEAP_USED_MB)
    val nativeHeap = metric(DiagnosticMetricId.PROCESS_NATIVE_HEAP_ALLOCATED_MB)
    val pss = metric(DiagnosticMetricId.PROCESS_PSS_MB)
    val allocated = metric(DiagnosticMetricId.PROCESS_ALLOCATED_MB)
    val gcCount = metric(DiagnosticMetricId.PROCESS_GC_COUNT)
    val gcTime = metric(DiagnosticMetricId.PROCESS_GC_TIME_MS)
    val projectionBuild = metric(DiagnosticMetricId.DIAGNOSTICS_UI_PROJECTION_BUILD_MS)
    val projectionAllocated = metric(DiagnosticMetricId.DIAGNOSTICS_UI_PROJECTION_ALLOCATED_KIB)
    val projectionCount = metric(DiagnosticMetricId.DIAGNOSTICS_UI_PROJECTION_COUNT)

    return PerformancePresentation(
        framesMeasured = frames.toLong().toString(),
        jankFrames = "${jankFrames.toLong()} (${jankPercent.formatMetric()}%)",
        firstDrawJank = count(DiagnosticMetricId.UI_JANK_FIRST_DRAW_FRAME_COUNT),
        transitionJank = count(DiagnosticMetricId.UI_JANK_WITHIN_500MS_OF_CONTEXT_CHANGE_COUNT),
        steadyJank = count(DiagnosticMetricId.UI_JANK_STEADY_STATE_COUNT),
        callbackDrops = count(DiagnosticMetricId.UI_FRAME_METRICS_CALLBACK_DROPPED_COUNT),
        frameBudget = frameBudget?.current?.formatWithUnit("ms") ?: "waiting",
        framePercentiles = frameTotal.percentiles("ms"),
        frameSamples = frameTotal.sampleWindow(),
        overrunPercentiles = overrun.tailPercentiles("ms"),
        vsyncDelayPercentiles = vsyncDelay.tailPercentiles("ms"),
        cpuPercentiles = cpu.percentiles("%"),
        javaHeap = javaHeap.trend("MiB"),
        nativeHeap = nativeHeap.trend("MiB"),
        pss = pss.trend("MiB"),
        allocated = allocated.trend("MiB"),
        gcCount = gcCount.delta("count"),
        gcTime = gcTime.delta("ms"),
        projectionBuild = projectionBuild.percentiles("ms"),
        projectionAllocated = projectionAllocated.percentiles("KiB"),
        projectionCount = projectionCount?.current?.toLong()?.toString() ?: "waiting",
        unknownDelay = metric(DiagnosticMetricId.UI_JANK_UNKNOWN_DELAY_MS).tailPercentiles("ms"),
        input = metric(DiagnosticMetricId.UI_JANK_INPUT_MS).tailPercentiles("ms"),
        animation = metric(DiagnosticMetricId.UI_JANK_ANIMATION_MS).tailPercentiles("ms"),
        layoutMeasure = metric(DiagnosticMetricId.UI_JANK_LAYOUT_MEASURE_MS).tailPercentiles("ms"),
        draw = metric(DiagnosticMetricId.UI_JANK_DRAW_MS).tailPercentiles("ms"),
        sync = metric(DiagnosticMetricId.UI_JANK_SYNC_MS).tailPercentiles("ms"),
        commandIssue = metric(DiagnosticMetricId.UI_JANK_COMMAND_ISSUE_MS).tailPercentiles("ms"),
        swapBuffers = metric(DiagnosticMetricId.UI_JANK_SWAP_BUFFERS_MS).tailPercentiles("ms"),
        gpu = metric(DiagnosticMetricId.UI_JANK_GPU_MS).tailPercentiles("ms"),
    )
}

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

private fun Double.formatMetric(): String = formatFixed3(signed = false)

private fun Double.formatSignedMetric(): String = formatFixed3(signed = true)

private fun Double.formatFixed3(signed: Boolean): String {
    if (!isFinite()) return toString()
    val scaled = (this * 1_000.0).roundToLong()
    val negative = scaled < 0L
    val magnitude = abs(scaled)
    val prefix = when {
        negative -> "-"
        signed -> "+"
        else -> ""
    }
    val whole = magnitude / 1_000L
    val fraction = (magnitude % 1_000L).toString().padStart(3, '0')
    return "$prefix$whole.$fraction"
}
