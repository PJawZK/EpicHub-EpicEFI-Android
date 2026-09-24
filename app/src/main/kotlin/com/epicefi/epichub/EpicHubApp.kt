package com.epicefi.epichub

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.epicefi.epichub.diagnostics.DiagnosticCapture
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
import com.epicefi.epichub.navigation.PrimaryWorkspace
import com.epicefi.epichub.platform.diagnostics.DiagnosticCaptureExporter
import com.epicefi.epichub.ui.EpicHubShell
import kotlinx.coroutines.delay

private const val RECENT_PROBLEM_POST_TRIGGER_NANOS = 3_000_000_000L

@Composable
fun EpicHubApp(
    diagnostics: DiagnosticHub,
    nativeEngineGeneration: Long,
) {
    val context = LocalContext.current
    val engine = remember { NativeEngine.create() }
    val nativeDiagnostics = remember { NativeDiagnosticIngestor() }
    val nativeEngineOwnerId = remember(nativeEngineGeneration) {
        "native-engine-$nativeEngineGeneration"
    }

    var destinationId by rememberSaveable { mutableStateOf(AppDestination.DASHBOARD.id) }
    var lastTunerDestinationId by rememberSaveable { mutableStateOf(AppDestination.TUNER.id) }
    var lastLoggerDestinationId by rememberSaveable { mutableStateOf(AppDestination.LOGGER.id) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var runtimeSnapshot by remember(engine) { mutableStateOf<RuntimeSnapshot?>(null) }
    var diagnosticSnapshot by remember { mutableStateOf(diagnostics.snapshotForUi()) }
    var pendingSaveCapture by remember { mutableStateOf<DiagnosticCapture?>(null) }

    val latestDestinationId by rememberUpdatedState(destinationId)
    val latestSettingsOpen by rememberUpdatedState(settingsOpen)

    fun refreshDiagnostics() {
        diagnosticSnapshot = diagnostics.snapshotForUi()
    }

    fun recordExportFailure(capture: DiagnosticCapture, error: Throwable) {
        diagnostics.invariantViolation(
            invariantId = "diagnostic.export.failed",
            detail = error.message ?: error::class.java.simpleName,
            domain = DiagnosticDomain.DIAGNOSTIC_CAPTURE,
            correlationId = "capture-${capture.captureId}",
        )
        refreshDiagnostics()
    }

    val saveCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination ->
        val capture = pendingSaveCapture
        pendingSaveCapture = null
        if (destination != null && capture != null) {
            runCatching {
                DiagnosticCaptureExporter.save(context, capture, destination)
            }.onFailure { error ->
                recordExportFailure(capture, error)
            }
        }
    }

    fun selectDestination(destination: AppDestination) {
        destinationId = destination.id
        when (destination.workspace) {
            PrimaryWorkspace.TUNER -> lastTunerDestinationId = destination.id
            PrimaryWorkspace.LOGGER -> lastLoggerDestinationId = destination.id
            else -> Unit
        }
        settingsOpen = false
        diagnostics.record(
            severity = DiagnosticSeverity.TRACE,
            domain = DiagnosticDomain.APP_UI,
            code = DiagnosticEventCode.APP_DESTINATION_CHANGED,
            payload = DiagnosticPayload.DestinationChanged(destination.id),
        )
        refreshDiagnostics()
    }

    fun selectWorkspace(workspace: PrimaryWorkspace) {
        val remembered = when (workspace) {
            PrimaryWorkspace.TUNER -> AppDestination.fromId(lastTunerDestinationId)
            PrimaryWorkspace.LOGGER -> AppDestination.fromId(lastLoggerDestinationId)
            else -> AppDestination.defaultFor(workspace)
        }
        val target = remembered.takeIf { it.workspace == workspace }
            ?: AppDestination.defaultFor(workspace)
        selectDestination(target)
    }

    DisposableEffect(engine, diagnostics, nativeEngineOwnerId) {
        diagnostics.createLifecycleOwner(
            ownerId = nativeEngineOwnerId,
            ownerKind = "NativeEngine",
            generation = nativeEngineGeneration,
            initialState = "active",
        )
        refreshDiagnostics()

        onDispose {
            engine.close()
            diagnostics.record(
                severity = DiagnosticSeverity.INFO,
                domain = DiagnosticDomain.NATIVE_ENGINE,
                code = DiagnosticEventCode.NATIVE_ENGINE_CLOSED,
                correlationId = nativeEngineOwnerId,
                payload = DiagnosticPayload.NativeEngineState(
                    alive = false,
                    activeEngineCount = NativeEngine.activeCount(),
                ),
            )
            diagnostics.destroyLifecycleOwner(nativeEngineOwnerId)
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

            val recentCaptureFinished = diagnostics.finishRecentProblemCaptureIfDue(
                RECENT_PROBLEM_POST_TRIGGER_NANOS,
            ) != null
            val diagnosticsVisible =
                AppDestination.fromId(latestDestinationId) == AppDestination.DIAGNOSTICS &&
                    !latestSettingsOpen
            if (diagnosticsVisible || recentCaptureFinished) {
                refreshDiagnostics()
            }
            delay(250)
        }
    }

    MaterialTheme {
        EpicHubShell(
            destination = AppDestination.fromId(destinationId),
            onDestinationSelected = ::selectDestination,
            onWorkspaceSelected = ::selectWorkspace,
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
                diagnostics.startRecentProblemCapture()
                refreshDiagnostics()
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
            onSaveCapture = { capture ->
                pendingSaveCapture = capture
                saveCaptureLauncher.launch(DiagnosticCaptureExporter.suggestedFileName(capture))
            },
            onShareCapture = { capture ->
                runCatching {
                    DiagnosticCaptureExporter.share(context, capture)
                }.onFailure { error ->
                    recordExportFailure(capture, error)
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
