package com.epicefi.epichub

import android.hardware.usb.UsbManager
import android.os.Debug
import android.os.SystemClock
import android.os.Trace
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
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
import com.epicefi.epichub.platform.profile.AndroidLiveTuneProfileDocumentSelectionOwner
import com.epicefi.epichub.platform.profile.EPICHUB_PROFILE_MIME_TYPE
import com.epicefi.epichub.platform.transport.usb.AndroidUsbCandidateDiscovery
import com.epicefi.epichub.platform.transport.usb.AndroidUsbPermissionRequestOwner
import com.epicefi.epichub.ui.EpicHubShell
import com.epicefi.epichub.ui.diagnostics.DiagnosticsTab
import com.epicefi.epichub.ui.settings.ProfilePackageSettingsState
import com.epicefi.epichub.ui.settings.UsbControllerSettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RECENT_PROBLEM_POST_TRIGGER_NANOS = 3_000_000_000L
private const val DIAGNOSTICS_ENTRY_SETTLE_MS = 400L
private const val DIAGNOSTICS_ONE_SHOT_SETTLE_MS = 150L
private const val BYTES_PER_KIBIBYTE = 1024.0

private val destinationPayloads = AppDestination.entries.associateWith { destination ->
    DiagnosticPayload.DestinationChanged(destination.id)
}

private val performanceDiagnosticMetrics = listOf(
    DiagnosticMetricId.UI_FRAME_COUNT,
    DiagnosticMetricId.UI_JANK_FRAME_COUNT,
    DiagnosticMetricId.UI_JANK_FIRST_DRAW_FRAME_COUNT,
    DiagnosticMetricId.UI_JANK_WITHIN_500MS_OF_CONTEXT_CHANGE_COUNT,
    DiagnosticMetricId.UI_JANK_STEADY_STATE_COUNT,
    DiagnosticMetricId.UI_FRAME_METRICS_CALLBACK_DROPPED_COUNT,
    DiagnosticMetricId.UI_FRAME_BUDGET_MS,
    DiagnosticMetricId.UI_FRAME_TOTAL_MS,
    DiagnosticMetricId.UI_FRAME_OVERRUN_MS,
    DiagnosticMetricId.UI_FRAME_VSYNC_DELAY_MS,
    DiagnosticMetricId.PROCESS_CPU_PERCENT,
    DiagnosticMetricId.PROCESS_JAVA_HEAP_USED_MB,
    DiagnosticMetricId.PROCESS_NATIVE_HEAP_ALLOCATED_MB,
    DiagnosticMetricId.PROCESS_PSS_MB,
    DiagnosticMetricId.PROCESS_GC_COUNT,
    DiagnosticMetricId.PROCESS_GC_TIME_MS,
    DiagnosticMetricId.PROCESS_ALLOCATED_MB,
    DiagnosticMetricId.DIAGNOSTICS_UI_PROJECTION_BUILD_MS,
    DiagnosticMetricId.DIAGNOSTICS_UI_PROJECTION_ALLOCATED_KIB,
    DiagnosticMetricId.DIAGNOSTICS_UI_PROJECTION_COUNT,
    DiagnosticMetricId.UI_JANK_UNKNOWN_DELAY_MS,
    DiagnosticMetricId.UI_JANK_INPUT_MS,
    DiagnosticMetricId.UI_JANK_ANIMATION_MS,
    DiagnosticMetricId.UI_JANK_LAYOUT_MEASURE_MS,
    DiagnosticMetricId.UI_JANK_DRAW_MS,
    DiagnosticMetricId.UI_JANK_SYNC_MS,
    DiagnosticMetricId.UI_JANK_COMMAND_ISSUE_MS,
    DiagnosticMetricId.UI_JANK_SWAP_BUFFERS_MS,
    DiagnosticMetricId.UI_JANK_GPU_MS,
)

@Composable
fun EpicHubApp(
    diagnostics: DiagnosticHub,
    nativeEngineGeneration: Long,
    benchmarkPlaceholderContent: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { NativeEngine.create() }
    val profileDocuments = remember(engine, context.contentResolver) {
        AndroidLiveTuneProfileDocumentSelectionOwner(engine, context.contentResolver)
    }
    val usbManager = remember(context) {
        checkNotNull(context.getSystemService(UsbManager::class.java)) {
            "Android USB manager is unavailable."
        }
    }
    val usbDiscovery = remember(usbManager) { AndroidUsbCandidateDiscovery(usbManager) }
    val nativeDiagnostics = remember { NativeDiagnosticIngestor() }
    val processPerformanceSampler = remember { ProcessPerformanceSampler() }
    val nativeEngineOwnerId = remember(nativeEngineGeneration) {
        "native-engine-$nativeEngineGeneration"
    }

    var destinationId by rememberSaveable { mutableStateOf(AppDestination.DASHBOARD.id) }
    var lastTunerDestinationId by rememberSaveable { mutableStateOf(AppDestination.TUNER.id) }
    var lastLoggerDestinationId by rememberSaveable { mutableStateOf(AppDestination.LOGGER.id) }
    var diagnosticsTabName by rememberSaveable { mutableStateOf(DiagnosticsTab.OVERVIEW.name) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var profilePackages by remember { mutableStateOf(ProfilePackageSettingsState()) }
    var usbController by remember { mutableStateOf(UsbControllerSettingsState()) }
    val usbPermissionOwner = remember(context, usbManager) {
        AndroidUsbPermissionRequestOwner(
            context = context,
            usbManager = usbManager,
            onCompletion = { completion ->
                usbController = usbController.permissionCompleted(completion)
            },
        )
    }
    val runtimeSnapshot = remember(engine) { mutableStateOf<RuntimeSnapshot?>(null) }
    val diagnosticSnapshotState = remember {
        mutableStateOf(
            DiagnosticsSnapshot(
                events = emptyList(),
                metrics = emptyList(),
                lifecycleOwners = emptyList(),
                captures = emptyList(),
                activeCapture = diagnostics.activeCapture(),
                eventCount = diagnostics.eventCount(),
                lifecycleOwnerCount = diagnostics.lifecycleOwnerCount(),
                captureCount = diagnostics.captureCount(),
            ),
            policy = referentialEqualityPolicy(),
        )
    }
    var activeCapture by remember { mutableStateOf(diagnostics.activeCapture()) }
    var pendingSaveCapture by remember { mutableStateOf<DiagnosticCapture?>(null) }

    fun selectedDiagnosticsTab(): DiagnosticsTab =
        DiagnosticsTab.entries.firstOrNull { it.name == diagnosticsTabName } ?: DiagnosticsTab.OVERVIEW

    fun diagnosticsVisible(): Boolean =
        AppDestination.fromId(destinationId) == AppDestination.DIAGNOSTICS && !settingsOpen

    fun restorePresentationContext() {
        val destination = AppDestination.fromId(destinationId)
        diagnostics.setCurrentDestination(
            if (destination == AppDestination.DIAGNOSTICS) {
                selectedDiagnosticsTab().contextId
            } else {
                destination.id
            },
        )
    }

    fun refreshCaptureState() {
        val nextActiveCapture = diagnostics.activeCapture()
        activeCapture = nextActiveCapture
        if (diagnosticsVisible()) {
            val previous = diagnosticSnapshotState.value
            if (previous.activeCapture != nextActiveCapture) {
                diagnosticSnapshotState.value = previous.copy(activeCapture = nextActiveCapture)
            }
        }
    }

    fun requestDiagnosticsRefresh(settleMillis: Long = DIAGNOSTICS_ONE_SHOT_SETTLE_MS) {
        scope.launch {
            if (settleMillis > 0L) {
                delay(settleMillis)
            }
            if (!diagnosticsVisible()) {
                return@launch
            }
            val tab = selectedDiagnosticsTab()
            val previous = diagnosticSnapshotState.value
            val next = withContext(Dispatchers.Default) {
                diagnostics.projectForUi(previous, tab)
            }
            if (
                diagnosticsVisible() &&
                selectedDiagnosticsTab() == tab &&
                next != previous
            ) {
                diagnosticSnapshotState.value = next
            }
        }
    }

    fun recordExportFailure(capture: DiagnosticCapture, error: Throwable) {
        diagnostics.invariantViolation(
            invariantId = "diagnostic.export.failed",
            detail = error.message ?: error::class.java.simpleName,
            domain = DiagnosticDomain.DIAGNOSTIC_CAPTURE,
            correlationId = "capture-${capture.captureId}",
        )
        if (diagnosticsVisible()) {
            requestDiagnosticsRefresh()
        }
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

    val profilePackagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            profilePackages = profilePackages.beginOperation()
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    profileDocuments.replace(uris)
                }
                profilePackages =
                    profilePackages.completeSelection(
                        result = result,
                        selectedDocumentCount = profileDocuments.selectedUris.size,
                    )
            }
        }
    }

    fun selectDestination(destination: AppDestination) {
        Trace.beginSection("EpicHub.selectDestination:${destination.id}")
        try {
            val changed = destinationId != destination.id
            destinationId = destination.id
            diagnostics.setCurrentDestination(destination.id)
            when (destination.workspace) {
                PrimaryWorkspace.TUNER -> lastTunerDestinationId = destination.id
                PrimaryWorkspace.LOGGER -> lastLoggerDestinationId = destination.id
                else -> Unit
            }
            settingsOpen = false
            if (changed) {
                diagnostics.record(
                    severity = DiagnosticSeverity.TRACE,
                    domain = DiagnosticDomain.APP_UI,
                    code = DiagnosticEventCode.APP_DESTINATION_CHANGED,
                    payload = destinationPayloads.getValue(destination),
                )
            }
        } finally {
            Trace.endSection()
        }
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

    DisposableEffect(
        engine,
        profileDocuments,
        usbPermissionOwner,
        diagnostics,
        nativeEngineOwnerId,
    ) {
        diagnostics.createLifecycleOwner(
            ownerId = nativeEngineOwnerId,
            ownerKind = "NativeEngine",
            generation = nativeEngineGeneration,
            initialState = "active",
        )

        onDispose {
            usbPermissionOwner.close()
            profileDocuments.close()
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

    LaunchedEffect(usbDiscovery) {
        val candidates = withContext(Dispatchers.Default) { usbDiscovery.discover() }
        usbController = usbController.refresh(candidates)
    }

    LaunchedEffect(destinationId, settingsOpen, diagnosticsTabName, diagnostics) {
        val visible =
            AppDestination.fromId(destinationId) == AppDestination.DIAGNOSTICS && !settingsOpen
        if (visible) {
            val tab = selectedDiagnosticsTab()
            diagnostics.setCurrentDestination(tab.contextId)
            delay(DIAGNOSTICS_ENTRY_SETTLE_MS)
            while (true) {
                val previous = diagnosticSnapshotState.value
                val next = withContext(Dispatchers.Default) {
                    diagnostics.projectForUi(previous, tab)
                }
                if (
                    diagnosticsVisible() &&
                    selectedDiagnosticsTab() == tab &&
                    next != previous
                ) {
                    diagnosticSnapshotState.value = next
                }
                delay(tab.refreshIntervalMillis())
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
            onOpenSettings = {
                settingsOpen = true
                diagnostics.setCurrentDestination("settings")
            },
            onCloseSettings = {
                settingsOpen = false
                restorePresentationContext()
                if (AppDestination.fromId(destinationId) == AppDestination.DIAGNOSTICS) {
                    requestDiagnosticsRefresh(DIAGNOSTICS_ENTRY_SETTLE_MS)
                }
            },
            runtimeSnapshot = runtimeSnapshot,
            diagnostics = diagnosticSnapshotState,
            activeCapture = activeCapture,
            engineAlive = engine.isAlive,
            activeEngineCount = NativeEngine.activeCount(),
            profilePackages = profilePackages,
            onSelectProfilePackages = {
                profilePackagePicker.launch(
                    arrayOf(EPICHUB_PROFILE_MIME_TYPE, "application/zip"),
                )
            },
            onClearProfilePackages = {
                if (!profilePackages.busy) {
                    profilePackages = profilePackages.beginOperation()
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            profileDocuments.clear()
                        }
                        profilePackages = profilePackages.completeClear(result)
                    }
                }
            },
            usbController = usbController,
            onRefreshUsbCandidates = {
                if (!usbController.permissionPending) {
                    scope.launch {
                        val candidates = withContext(Dispatchers.Default) {
                            usbDiscovery.discover()
                        }
                        usbController = usbController.refresh(candidates)
                    }
                }
            },
            onSelectUsbCandidate = { endpointId ->
                if (!usbController.permissionPending) {
                    usbController = usbController.select(endpointId)
                }
            },
            onRequestUsbPermission = {
                val selected = usbController.selectedCandidate
                if (
                    selected != null &&
                    profilePackages.hasSelection &&
                    !usbController.permissionPending
                ) {
                    usbController =
                        usbController.permissionStarted(
                            usbPermissionOwner.request(selected),
                        )
                }
            },
            benchmarkPlaceholderContent = benchmarkPlaceholderContent,
            onDiagnosticsTabSelected = { tab ->
                if (diagnosticsTabName != tab.name) {
                    diagnosticsTabName = tab.name
                }
                if (diagnosticsVisible()) {
                    diagnostics.setCurrentDestination(tab.contextId)
                }
            },
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
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            DiagnosticCaptureExporter.createShareIntent(context, capture)
                        }
                    }
                    result.onSuccess { chooser ->
                        context.startActivity(chooser)
                    }.onFailure { error ->
                        recordExportFailure(capture, error)
                    }
                }
            },
        )
    }
}

private fun DiagnosticsTab.refreshIntervalMillis(): Long = when (this) {
    DiagnosticsTab.PERFORMANCE -> 1_000L
    DiagnosticsTab.METRICS -> 1_500L
    DiagnosticsTab.EVENTS -> 1_500L
    DiagnosticsTab.OVERVIEW,
    DiagnosticsTab.SESSIONS,
    DiagnosticsTab.CAPTURES -> 2_000L
}

private fun DiagnosticHub.projectForUi(
    previous: DiagnosticsSnapshot,
    tab: DiagnosticsTab,
): DiagnosticsSnapshot = measureUiProjection {
    val active = activeCapture()
    val nextEventCount = eventCount()
    val nextLifecycleOwnerCount = lifecycleOwnerCount()
    val nextCaptureCount = captureCount()

    when (tab) {
        DiagnosticsTab.OVERVIEW -> previous.copy(
            activeCapture = active,
            eventCount = nextEventCount,
            lifecycleOwnerCount = nextLifecycleOwnerCount,
            captureCount = nextCaptureCount,
        )

        DiagnosticsTab.PERFORMANCE -> previous.copy(
            metrics = metricSummaries(performanceDiagnosticMetrics),
            activeCapture = active,
            eventCount = nextEventCount,
            lifecycleOwnerCount = nextLifecycleOwnerCount,
            captureCount = nextCaptureCount,
        )

        DiagnosticsTab.METRICS -> previous.copy(
            metrics = metricSummaries(),
            activeCapture = active,
            eventCount = nextEventCount,
            lifecycleOwnerCount = nextLifecycleOwnerCount,
            captureCount = nextCaptureCount,
        )

        DiagnosticsTab.EVENTS -> {
            val nextEvents = events()
            previous.copy(
                events = nextEvents,
                activeCapture = active,
                eventCount = nextEvents.size,
                lifecycleOwnerCount = nextLifecycleOwnerCount,
                captureCount = nextCaptureCount,
            )
        }

        DiagnosticsTab.SESSIONS -> {
            val nextOwners = lifecycleOwners()
            previous.copy(
                lifecycleOwners = nextOwners,
                activeCapture = active,
                eventCount = nextEventCount,
                lifecycleOwnerCount = nextOwners.size,
                captureCount = nextCaptureCount,
            )
        }

        DiagnosticsTab.CAPTURES -> {
            val nextCaptures = captures()
            previous.copy(
                captures = nextCaptures,
                activeCapture = active,
                eventCount = nextEventCount,
                lifecycleOwnerCount = nextLifecycleOwnerCount,
                captureCount = nextCaptures.size,
            )
        }
    }
}

private inline fun DiagnosticHub.measureUiProjection(
    block: () -> DiagnosticsSnapshot,
): DiagnosticsSnapshot {
    val allocatedBefore = runtimeAllocatedBytes()
    val startedAt = SystemClock.elapsedRealtimeNanos()
    val result = block()
    val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
    val allocatedAfter = runtimeAllocatedBytes()

    observeMetric(DiagnosticMetricId.DIAGNOSTICS_UI_PROJECTION_BUILD_MS, elapsedMs)
    if (allocatedBefore != null && allocatedAfter != null && allocatedAfter >= allocatedBefore) {
        observeMetric(
            DiagnosticMetricId.DIAGNOSTICS_UI_PROJECTION_ALLOCATED_KIB,
            (allocatedAfter - allocatedBefore).toDouble() / BYTES_PER_KIBIBYTE,
        )
    }
    incrementMetric(DiagnosticMetricId.DIAGNOSTICS_UI_PROJECTION_COUNT)
    return result
}

private fun runtimeAllocatedBytes(): Long? =
    runCatching { Debug.getRuntimeStat("art.gc.bytes-allocated").toLongOrNull() }.getOrNull()
