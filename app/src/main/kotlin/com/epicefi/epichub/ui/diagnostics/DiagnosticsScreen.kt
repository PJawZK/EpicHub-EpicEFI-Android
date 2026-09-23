package com.epicefi.epichub.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.diagnostics.DiagnosticCapture
import com.epicefi.epichub.diagnostics.DiagnosticsSnapshot
import com.epicefi.epichub.ui.components.KeyValueRow
import com.epicefi.epichub.ui.components.StructuralCard
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticsScreen(
    diagnostics: DiagnosticsSnapshot,
    onExportCapture: (DiagnosticCapture) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Events", "Metrics", "Sessions", "Captures")

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        TabRow(selectedTabIndex = selectedTab) {
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
            1 -> EventsTab(diagnostics)
            2 -> MetricsTab(diagnostics)
            3 -> SessionsTab(diagnostics)
            else -> CapturesTab(diagnostics, onExportCapture)
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
                KeyValueRow("Saved captures", diagnostics.captures.size.toString())
                KeyValueRow(
                    "Capture state",
                    diagnostics.activeCapture?.let { "${it.mode} #${it.captureId}" } ?: "idle",
                )
            }
        }
        item {
            StructuralCard(
                title = "Subsystem status",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "Current foundation coverage",
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = diagnostics.metrics,
            key = { it.metric.stableId },
        ) { metric ->
            StructuralCard(
                title = metric.metric.stableId,
                modifier = Modifier.fillMaxWidth(),
                subtitle = metric.metric.unit,
            ) {
                KeyValueRow("Current", metric.current.formatMetric())
                KeyValueRow("Min / Max", "${metric.minimum.formatMetric()} / ${metric.maximum.formatMetric()}")
                KeyValueRow("p50 / p95 / p99", "${metric.p50.formatMetric()} / ${metric.p95.formatMetric()} / ${metric.p99.formatMetric()}")
                KeyValueRow("Samples", metric.sampleCount.toString())
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
    onExportCapture: (DiagnosticCapture) -> Unit,
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
                    "No finalized captures yet. Use the global Capture control while reproducing a problem.",
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(
            items = diagnostics.captures,
            key = { it.captureId },
        ) { capture ->
            StructuralCard(
                title = "Capture #${capture.captureId}",
                modifier = Modifier.fillMaxWidth(),
                subtitle = DateFormat.getDateTimeInstance().format(Date(capture.createdAtWallClockMillis)),
            ) {
                KeyValueRow("Mode", capture.mode.name)
                KeyValueRow("Events", capture.events.size.toString())
                KeyValueRow("Markers", capture.markers.size.toString())
                Button(onClick = { onExportCapture(capture) }) {
                    Text("Export capture")
                }
            }
        }
    }
}

private fun Double.formatMetric(): String = "%.3f".format(this)
