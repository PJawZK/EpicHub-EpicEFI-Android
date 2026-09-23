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
}

data class DiagnosticMetricSummary(
    val metric: DiagnosticMetricId,
    val sampleCount: Long,
    val current: Double,
    val minimum: Double,
    val maximum: Double,
    val p50: Double,
    val p95: Double,
    val p99: Double,
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
    val note: String?,
)

data class ActiveDiagnosticCapture(
    val captureId: Long,
    val mode: DiagnosticCaptureMode,
    val startedAtNanos: Long,
    val triggerAtNanos: Long?,
    val markerCount: Int,
)
