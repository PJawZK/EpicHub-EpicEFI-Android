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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.epicefi.epichub.platform.diagnostics.ProcessPerformanceSampler
import com.epicefi.epichub.ui.EpicHubShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RECENT_PROBLEM_POST_TRIGGER_NANOS = 3_000_000_000L
private const val DIAGNOSTICS_PRESENTATION_REFRESH_MS = 1_000L

@Composable
fun EpicHubApp(
    diagnostics: DiagnosticHub,
    nativeEngineGeneration: Long,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { NativeEngine.create() }
    val nativeDiagnostics = remember { NativeDiagnosticIngestor() }
    val processPerformanceSampler = remember { ProcessPerformanceSampler() }
    val nativeEngineOwnerId = remember(nativeEngineGeneration) {
        "native-engine-$nativeEngineGeneration"
    }

    var destinationId by rememberSaveable { mutableStateOf(AppDestination.DASHBOARD.id) }
    var lastTunerDestinationId by rememberSaveable { mutableStateOf(AppDestination.TUNER.id) }
    var lastLoggerDestinationId by rememberSaveable { mutableStateOf(AppDestination.LOGGER.id) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val runtimeSnapshot = remember(engine) { mutableStateOf<RuntimeSnapshot?>(null) }
    var diagnosticSnapshot by remember {
        mutableStateOf(
            DiagnosticsSnapshot(
                events = emptyList(),
                metrics = emptyList(),
                lifecycleOwners = emptyList(),
                captures = emptyList(),
                activeCapture = diagnostics.activeCapture(),
            ),
        )
    }
    var pendingSaveCapture by remember { mutableStateOf<DiagnosticCapture?>(null) }

    fun diagnosticsVisible(): Boolean =
        AppDestination.fromId(destinationId) == AppDestination.DIAGNOSTICS && !settingsOpen

    fun refreshCaptureState() {
        diagnosticSnapshot = diagnosticSnapshot.copy(activeCapture = diagnostics.activeCapture())
    }

    fun requestDiagnosticsRefresh() {
        scope.launch {
            val next = withContext(Dispatchers.Default) {
                diagnostics.snapshotForUi()
            }
            diagnosticSnapshot = next
        }
    }

    fun recordExportFailure(capture: DiagnosticCapture, error: Throwable) {
        diagnostics.invariantViolation(
            invariantId = "diagnostic.export.failed",
            detail = error.message ?: error::class.java.simpleName,
            domain = DiagnosticDomain.DIAGNOSTIC_CAPTURE,
            correlationId = "capture-${capture.captureId}",
        )
        requestDiagnosticsRefresh()
    }

    val saveCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination ->
        val capture = pendingSaveCapture
        pendingSaveCapture = null
        if (destination != null && capture != null) {
            scope.launch {
                val failure = runCatching {
                    withContext(Dispatchers.IO) {
                        DiagnosticCaptureExporter.save(context, capture, destination)
                    }
                }.exceptionOrNull()
                if (failure != null) {
                    recordExportFailure(capture, failure)
                }
            }
        }
    }

    fun selectDestination(destination: AppDestination) {
        destinationId = destination.id
        diagnostics.setCurrentDestination(destination.id)
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

    LaunchedEffect(destinationId, diagnostics) {
        diagnostics.setCurrentDestination(destinationId)
    }

    LaunchedEffect(destinationId, settingsOpen, diagnostics) {
        val visible =
            AppDestination.fromId(destinationId) == AppDestination.DIAGNOSTICS && !settingsOpen
        if (visible) {
            while (true) {
                diagnosticSnapshot = withContext(Dispatchers.Default) {
                    diagnostics.snapshotForUi()
                }
                delay(DIAGNOSTICS_PRESENTATION_REFRESH_MS)
            }
        }
    }

    LaunchedEffect(diagnostics, processPerformanceSampler) {
        while (true) {
            delay(1_000)
            withContext(Dispatchers.Default) {
                processPerformanceSampler.sample(diagnostics)
            }
        }
    }

    LaunchedEffect(engine, diagnostics, nativeDiagnostics) {
        while (true) {
            val next = withContext(Dispatchers.Default) {
                val startedAt = SystemClock.elapsedRealtimeNanos()
                val snapshot = engine.readRuntimeSnapshot()
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
                diagnostics.observeMetric(DiagnosticMetricId.RUNTIME_SNAPSHOT_READ_MS, elapsedMs)
                diagnostics.observeMetric(DiagnosticMetricId.RUNTIME_SNAPSHOT_SEQUENCE, snapshot.sequence.toDouble())
                diagnostics.observeMetric(
                    DiagnosticMetricId.ACTIVE_NATIVE_ENGINES,
                    NativeEngine.activeCount().toDouble(),
                )

                runCatching { engine.drainDiagnosticEvents() }
                    .onSuccess { batch ->
                        nativeDiagnostics.ingest(
                            batch = batch,
                            diagnostics = diagnostics,
                            sessionId = snapshot.sessionId,
                        )
                    }
                    .onFailure { error ->
                        diagnostics.invariantViolation(
                            invariantId = "native.diagnostics.drain_failed",
                            detail = error.message ?: error::class.java.simpleName,
                            domain = DiagnosticDomain.NATIVE_ENGINE,
                            correlationId = "native-session-${snapshot.sessionId}",
                        )
                    }
                snapshot
            }
            runtimeSnapshot.value = next

            val recentCaptureFinished = withContext(Dispatchers.Default) {
                diagnostics.finishRecentProblemCaptureIfDue(
                    RECENT_PROBLEM_POST_TRIGGER_NANOS,
                )
            } != null
            if (recentCaptureFinished) {
                refreshCaptureState()
                if (diagnosticsVisible()) {
                    requestDiagnosticsRefresh()
                }
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
                    requestDiagnosticsRefresh()
                }
            },
            runtimeSnapshot = runtimeSnapshot,
            diagnostics = diagnosticSnapshot,
            engineAlive = engine.isAlive,
            activeEngineCount = NativeEngine.activeCount(),
            onCaptureRecentProblem = {
                diagnostics.startRecentProblemCapture()
                refreshCaptureState()
            },
            onStartReproductionCapture = {
                diagnostics.startReproductionCapture()
                refreshCaptureState()
            },
            onAddCaptureMarker = {
                diagnostics.addMarker()
                refreshCaptureState()
            },
            onStopCapture = {
                scope.launch {
                    val finished = withContext(Dispatchers.Default) {
                        if (diagnostics.activeCapture() != null) {
                            diagnostics.finishCapture()
                        } else {
                            null
                        }
                    }
                    refreshCaptureState()
                    if (finished != null && diagnosticsVisible()) {
                        requestDiagnosticsRefresh()
                    }
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
