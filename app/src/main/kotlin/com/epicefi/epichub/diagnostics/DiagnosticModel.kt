package com.epicefi.epichub.diagnostics

enum class DiagnosticSeverity {
    TRACE,
    INFO,
    WARNING,
    ERROR,
    CRITICAL,
}

enum class DiagnosticDomain {
    APP_UI,
    NATIVE_ENGINE,
    RUNTIME,
    USB_ECU,
    BLE_ACCESSORY,
    LOGGER_STORAGE,
    TUNING,
    ANALYZER,
    TUNING_TOOLS,
    LIFECYCLE,
    DIAGNOSTIC_CAPTURE,
}

enum class DiagnosticEventCode(val stableId: String) {
    APP_STARTED("app.started"),
    APP_DESTINATION_CHANGED("app.destination.changed"),
    NATIVE_ENGINE_CREATED("native.engine.created"),
    NATIVE_ENGINE_CLOSED("native.engine.closed"),
    NATIVE_ENGINE_EVENT("native.engine.event"),
    RUNTIME_SNAPSHOT_OBSERVED("runtime.snapshot.observed"),
    LIFECYCLE_OWNER_CREATED("lifecycle.owner.created"),
    LIFECYCLE_OWNER_STATE_CHANGED("lifecycle.owner.state_changed"),
    LIFECYCLE_OWNER_DESTROYED("lifecycle.owner.destroyed"),
    LATE_EVENT_AFTER_OWNER_DESTROYED("lifecycle.owner.late_event"),
    CAPTURE_STARTED("diagnostics.capture.started"),
    CAPTURE_MARKER_ADDED("diagnostics.capture.marker"),
    CAPTURE_FINISHED("diagnostics.capture.finished"),
    INVARIANT_VIOLATION("diagnostics.invariant.violation"),
}

sealed interface DiagnosticPayload {
    data object None : DiagnosticPayload

    data class DestinationChanged(
        val destinationId: String,
    ) : DiagnosticPayload

    data class NativeEngineState(
        val alive: Boolean,
        val activeEngineCount: Int,
    ) : DiagnosticPayload

    data class NativeEngineEvent(
        val nativeEventId: Long,
        val engineElapsedNanos: Long,
        val rawCode: Int,
        val arg0: Long,
        val arg1: Long,
    ) : DiagnosticPayload

    data class RuntimeSnapshotState(
        val sequence: Long,
        val sessionId: Long,
        val sourceKind: Int,
        val channelCount: Int,
    ) : DiagnosticPayload

    data class LifecycleState(
        val ownerId: String,
        val ownerKind: String,
        val state: String,
        val generation: Long,
    ) : DiagnosticPayload

    data class CaptureState(
        val captureId: Long,
        val mode: DiagnosticCaptureMode,
        val markerCount: Int,
    ) : DiagnosticPayload

    data class InvariantViolation(
        val invariantId: String,
        val detail: String,
    ) : DiagnosticPayload
}

data class DiagnosticEvent(
    val eventId: Long,
    val monotonicNanos: Long,
    val wallClockMillis: Long,
    val severity: DiagnosticSeverity,
    val domain: DiagnosticDomain,
    val code: DiagnosticEventCode,
    val correlationId: String?,
    val sessionId: Long?,
    val payload: DiagnosticPayload,
)

enum class DiagnosticMetricKind {
    COUNTER,
    GAUGE,
    DISTRIBUTION,
}

enum class DiagnosticMetricId(
    val stableId: String,
    val unit: String,
    val kind: DiagnosticMetricKind,
) {
    RUNTIME_SNAPSHOT_READ_MS(
        stableId = "runtime.snapshot.read_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    RUNTIME_SNAPSHOT_SEQUENCE(
        stableId = "runtime.snapshot.sequence",
        unit = "count",
        kind = DiagnosticMetricKind.GAUGE,
    ),
    PROCESS_CPU_PERCENT(
        stableId = "process.cpu.percent_of_one_core",
        unit = "%",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    PROCESS_JAVA_HEAP_USED_MB(
        stableId = "process.memory.java_heap_used_mb",
        unit = "MiB",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    PROCESS_NATIVE_HEAP_ALLOCATED_MB(
        stableId = "process.memory.native_heap_allocated_mb",
        unit = "MiB",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    PROCESS_PSS_MB(
        stableId = "process.memory.pss_mb",
        unit = "MiB",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    PROCESS_GC_COUNT(
        stableId = "process.runtime.gc_count",
        unit = "count",
        kind = DiagnosticMetricKind.GAUGE,
    ),
    PROCESS_GC_TIME_MS(
        stableId = "process.runtime.gc_time_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.GAUGE,
    ),
    PROCESS_ALLOCATED_MB(
        stableId = "process.runtime.bytes_allocated_mb",
        unit = "MiB",
        kind = DiagnosticMetricKind.GAUGE,
    ),
    UI_FRAME_COUNT(
        stableId = "ui.frame.total_count",
        unit = "frames",
        kind = DiagnosticMetricKind.COUNTER,
    ),
    UI_JANK_FRAME_COUNT(
        stableId = "ui.frame.jank_count",
        unit = "frames",
        kind = DiagnosticMetricKind.COUNTER,
    ),
    UI_JANK_FIRST_DRAW_FRAME_COUNT(
        stableId = "ui.frame.jank_first_draw_count",
        unit = "frames",
        kind = DiagnosticMetricKind.COUNTER,
    ),
    UI_JANK_WITHIN_500MS_OF_CONTEXT_CHANGE_COUNT(
        stableId = "ui.frame.jank_within_500ms_of_context_change_count",
        unit = "frames",
        kind = DiagnosticMetricKind.COUNTER,
    ),
    UI_JANK_STEADY_STATE_COUNT(
        stableId = "ui.frame.jank_steady_state_count",
        unit = "frames",
        kind = DiagnosticMetricKind.COUNTER,
    ),
    UI_FRAME_METRICS_CALLBACK_DROPPED_COUNT(
        stableId = "ui.frame.metrics_callback_dropped_count",
        unit = "frames",
        kind = DiagnosticMetricKind.COUNTER,
    ),
    UI_FRAME_BUDGET_MS(
        stableId = "ui.frame.budget_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.GAUGE,
    ),
    UI_FRAME_TOTAL_MS(
        stableId = "ui.frame.total_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    UI_FRAME_DEADLINE_MS(
        stableId = "ui.frame.deadline_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    UI_FRAME_OVERRUN_MS(
        stableId = "ui.frame.overrun_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    UI_FRAME_VSYNC_DELAY_MS(
        stableId = "ui.frame.vsync_delay_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    UI_JANK_UNKNOWN_DELAY_MS(
        stableId = "ui.jank.unknown_delay_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    UI_JANK_INPUT_MS(
        stableId = "ui.jank.input_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    UI_JANK_ANIMATION_MS(
        stableId = "ui.jank.animation_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    UI_JANK_LAYOUT_MEASURE_MS(
        stableId = "ui.jank.layout_measure_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    UI_JANK_DRAW_MS(
        stableId = "ui.jank.draw_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    UI_JANK_SYNC_MS(
        stableId = "ui.jank.sync_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    UI_JANK_COMMAND_ISSUE_MS(
        stableId = "ui.jank.command_issue_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    UI_JANK_SWAP_BUFFERS_MS(
        stableId = "ui.jank.swap_buffers_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    UI_JANK_GPU_MS(
        stableId = "ui.jank.gpu_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    ACTIVE_NATIVE_ENGINES(
        stableId = "native.engine.active_count",
        unit = "count",
        kind = DiagnosticMetricKind.GAUGE,
    ),
    DIAGNOSTIC_EVENT_COUNT(
        stableId = "diagnostics.events.total",
        unit = "count",
        kind = DiagnosticMetricKind.COUNTER,
    ),
    DIAGNOSTIC_DROPPED_EVENT_COUNT(
        stableId = "diagnostics.events.dropped_total",
        unit = "events",
        kind = DiagnosticMetricKind.COUNTER,
    ),
    DIAGNOSTIC_RING_SIZE(
        stableId = "diagnostics.events.ring_size",
        unit = "events",
        kind = DiagnosticMetricKind.GAUGE,
    ),
    DIAGNOSTIC_CAPTURE_COUNT(
        stableId = "diagnostics.captures.total",
        unit = "count",
        kind = DiagnosticMetricKind.COUNTER,
    ),
    DIAGNOSTICS_UI_PROJECTION_BUILD_MS(
        stableId = "diagnostics.ui.projection_build_ms",
        unit = "ms",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    DIAGNOSTICS_UI_PROJECTION_ALLOCATED_KIB(
        stableId = "diagnostics.ui.projection_allocated_kib",
        unit = "KiB",
        kind = DiagnosticMetricKind.DISTRIBUTION,
    ),
    DIAGNOSTICS_UI_PROJECTION_COUNT(
        stableId = "diagnostics.ui.projection_count",
        unit = "count",
        kind = DiagnosticMetricKind.COUNTER,
    ),
}

data class DiagnosticMetricSummary(
    val metric: DiagnosticMetricId,
    val sampleCount: Long,
    val retainedSampleCount: Int,
    val first: Double,
    val current: Double,
    val minimum: Double,
    val maximum: Double,
    val p50: Double,
    val p95: Double,
    val p99: Double,
)

data class DiagnosticWorstFrame(
    val frameNumber: Long,
    val monotonicNanos: Long,
    val wallClockMillis: Long,
    val destinationId: String,
    val contextAgeMs: Double,
    val firstDrawFrame: Boolean,
    val totalMs: Double,
    val budgetMs: Double,
    val overrunMs: Double,
    val vsyncDelayMs: Double?,
    val unknownDelayMs: Double?,
    val animationMs: Double?,
    val layoutMeasureMs: Double?,
    val drawMs: Double?,
    val syncMs: Double?,
    val commandIssueMs: Double?,
    val swapBuffersMs: Double?,
    val gpuMs: Double?,
)

enum class DiagnosticCaptureMode {
    RECENT_PROBLEM,
    REPRODUCTION,
}

data class DiagnosticMarker(
    val markerId: Long,
    val monotonicNanos: Long,
    val wallClockMillis: Long,
    val label: String?,
)

data class DiagnosticLifecycleOwner(
    val ownerId: String,
    val ownerKind: String,
    val generation: Long,
    val createdAtNanos: Long,
    val currentState: String,
    val destroyedAtNanos: Long?,
    val lateEventCount: Long,
)

data class DiagnosticCapture(
    val captureId: Long,
    val mode: DiagnosticCaptureMode,
    val startedAtNanos: Long,
    val triggerAtNanos: Long?,
    val stoppedAtNanos: Long,
    val createdAtWallClockMillis: Long,
    val events: List<DiagnosticEvent>,
    val metrics: List<DiagnosticMetricSummary>,
    val lifecycleOwners: List<DiagnosticLifecycleOwner>,
    val markers: List<DiagnosticMarker>,
    val worstFrames: List<DiagnosticWorstFrame>,
    val note: String?,
)

data class ActiveDiagnosticCapture(
    val captureId: Long,
    val mode: DiagnosticCaptureMode,
    val startedAtNanos: Long,
    val triggerAtNanos: Long?,
    val markerCount: Int,
)

data class DiagnosticsSnapshot(
    val events: List<DiagnosticEvent>,
    val metrics: List<DiagnosticMetricSummary>,
    val lifecycleOwners: List<DiagnosticLifecycleOwner>,
    val captures: List<DiagnosticCapture>,
    val activeCapture: ActiveDiagnosticCapture?,
    val eventCount: Int = events.size,
    val lifecycleOwnerCount: Int = lifecycleOwners.size,
    val captureCount: Int = captures.size,
)
