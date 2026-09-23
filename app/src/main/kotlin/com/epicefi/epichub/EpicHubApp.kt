package com.epicefi.epichub

import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.epicefi.epichub.diagnostics.DiagnosticDomain
import com.epicefi.epichub.diagnostics.DiagnosticEventCode
import com.epicefi.epichub.diagnostics.DiagnosticHub
import com.epicefi.epichub.diagnostics.DiagnosticMetricId
import com.epicefi.epichub.diagnostics.DiagnosticPayload
import com.epicefi.epichub.diagnostics.DiagnosticSeverity
import com.epicefi.epichub.diagnostics.DiagnosticsSnapshot
import com.epicefi.epichub.diagnostics.NativeDiagnosticIngestor
import com.epicefi.epichub.engine.NativeEngine
import com.epicefi.epichub.engine.RuntimeSnapshot
import com.epicefi.epichub.navigation.AppDestination
import com.epicefi.epichub.platform.diagnostics.DiagnosticCaptureExporter
import com.epicefi.epichub.ui.EpicHubShell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EpicHubApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { NativeEngine.create() }
    val diagnostics = remember { DiagnosticHub() }
    val nativeDiagnostics = remember { NativeDiagnosticIngestor() }

    var destinationId by rememberSaveable { mutableStateOf(AppDestination.DASHBOARD.id) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var runtimeSnapshot by remember(engine) { mutableStateOf<RuntimeSnapshot?>(null) }
    var diagnosticSnapshot by remember { mutableStateOf(diagnostics.snapshotForUi()) }

    val latestDestinationId by rememberUpdatedState(destinationId)
    val latestSettingsOpen by rememberUpdatedState(settingsOpen)

    fun refreshDiagnostics() {
        diagnosticSnapshot = diagnostics.snapshotForUi()
    }

    DisposableEffect(engine, diagnostics) {
        diagnostics.createLifecycleOwner(
            ownerId = "epichub-composition",
            ownerKind = "ComposeRoot",
            generation = 1,
            initialState = "active",
        )
        diagnostics.createLifecycleOwner(
            ownerId = "native-engine-1",
            ownerKind = "NativeEngine",
            generation = 1,
            initialState = "active",
        )
        diagnostics.record(
            severity = DiagnosticSeverity.INFO,
            domain = DiagnosticDomain.APP_UI,
            code = DiagnosticEventCode.APP_STARTED,
        )
        refreshDiagnostics()

        onDispose {
            diagnostics.destroyLifecycleOwner("native-engine-1")
            engine.close()
            diagnostics.record(
                severity = DiagnosticSeverity.INFO,
                domain = DiagnosticDomain.NATIVE_ENGINE,
                code = DiagnosticEventCode.NATIVE_ENGINE_CLOSED,
                payload = DiagnosticPayload.NativeEngineState(
                    alive = false,
                    activeEngineCount = NativeEngine.activeCount(),
                ),
            )
            diagnostics.destroyLifecycleOwner("epichub-composition")
        }
    }

    LaunchedEffect(engine, diagnostics, nativeDiagnostics) {
        while (true) {
            val startedAt = SystemClock.elapsedRealtimeNanos()
            val next = engine.readRuntimeSnapshot()
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
            runtimeSnapshot = next
            diagnostics.observeMetric(DiagnosticMetricId.RUNTIME_SNAPSHOT_READ_MS, elapsedMs)
            diagnostics.observeMetric(DiagnosticMetricId.RUNTIME_SNAPSHOT_SEQUENCE, next.sequence.toDouble())
            diagnostics.observeMetric(
                DiagnosticMetricId.ACTIVE_NATIVE_ENGINES,
                NativeEngine.activeCount().toDouble(),
            )

            runCatching { engine.drainDiagnosticEvents() }
                .onSuccess { batch ->
                    nativeDiagnostics.ingest(
                        batch = batch,
                        diagnostics = diagnostics,
                        sessionId = next.sessionId,
                    )
                }
                .onFailure { error ->
                    diagnostics.invariantViolation(
                        invariantId = "native.diagnostics.drain_failed",
                        detail = error.message ?: error::class.java.simpleName,
                        domain = DiagnosticDomain.NATIVE_ENGINE,
                        correlationId = "native-session-${next.sessionId}",
                    )
                }

            val diagnosticsVisible =
                AppDestination.fromId(latestDestinationId) == AppDestination.DIAGNOSTICS &&
                    !latestSettingsOpen
            if (diagnosticsVisible) {
                refreshDiagnostics()
            }
            delay(250)
        }
    }

    MaterialTheme {
        EpicHubShell(
            destination = AppDestination.fromId(destinationId),
            onDestinationSelected = { destination ->
                destinationId = destination.id
                settingsOpen = false
                diagnostics.record(
                    severity = DiagnosticSeverity.TRACE,
                    domain = DiagnosticDomain.APP_UI,
                    code = DiagnosticEventCode.APP_DESTINATION_CHANGED,
                    payload = DiagnosticPayload.DestinationChanged(destination.id),
                )
                refreshDiagnostics()
            },
            settingsOpen = settingsOpen,
            onOpenSettings = { settingsOpen = true },
            onCloseSettings = {
                settingsOpen = false
                if (AppDestination.fromId(destinationId) == AppDestination.DIAGNOSTICS) {
                    refreshDiagnostics()
                }
            },
            runtimeSnapshot = runtimeSnapshot,
            diagnostics = diagnosticSnapshot,
            engineAlive = engine.isAlive,
            activeEngineCount = NativeEngine.activeCount(),
            onCaptureRecentProblem = {
                val capture = diagnostics.startRecentProblemCapture()
                refreshDiagnostics()
                scope.launch {
                    delay(3_000)
                    if (diagnostics.activeCapture()?.captureId == capture.captureId) {
                        diagnostics.finishCapture()
                        refreshDiagnostics()
                    }
                }
            },
            onStartReproductionCapture = {
                diagnostics.startReproductionCapture()
                refreshDiagnostics()
            },
            onAddCaptureMarker = {
                diagnostics.addMarker()
                refreshDiagnostics()
            },
            onStopCapture = {
                if (diagnostics.activeCapture() != null) {
                    diagnostics.finishCapture()
                    refreshDiagnostics()
                }
            },
            onExportCapture = { capture ->
                runCatching {
                    DiagnosticCaptureExporter.share(context, capture)
                }.onFailure { error ->
                    diagnostics.invariantViolation(
                        invariantId = "diagnostic.export.failed",
                        detail = error.message ?: error::class.java.simpleName,
                        domain = DiagnosticDomain.DIAGNOSTIC_CAPTURE,
                        correlationId = "capture-${capture.captureId}",
                    )
                    refreshDiagnostics()
                }
            },
        )
    }
}

private fun DiagnosticHub.snapshotForUi(): DiagnosticsSnapshot = DiagnosticsSnapshot(
    events = events(),
    metrics = metricSummaries(),
    lifecycleOwners = lifecycleOwners(),
    captures = captures(),
    activeCapture = activeCapture(),
)
