package com.epicefi.epichub.diagnostics

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

class DiagnosticHub(
    eventCapacity: Int = 512,
    private val retainedCaptureCount: Int = 20,
) {
    init {
        require(retainedCaptureCount > 0) { "Retained capture count must be positive." }
    }

    private data class CaptureBuilder(
        val captureId: Long,
        val mode: DiagnosticCaptureMode,
        val startedAtNanos: Long,
        val triggerAtNanos: Long?,
        val firstLiveEventId: Long,
        val preTriggerEvents: List<DiagnosticEvent>,
        val markers: MutableList<DiagnosticMarker>,
    )

    private val nextEventId = AtomicLong(1)
    private val nextCaptureId = AtomicLong(1)
    private val nextMarkerId = AtomicLong(1)
    private val ring = DiagnosticRing(eventCapacity)
    private val metrics = DiagnosticMetrics()
    private val lifecycle = DiagnosticLifecycleRegistry()
    private val captures = ArrayDeque<DiagnosticCapture>()

    @Volatile
    private var activeCapture: CaptureBuilder? = null

    fun record(
        severity: DiagnosticSeverity,
        domain: DiagnosticDomain,
        code: DiagnosticEventCode,
        correlationId: String? = null,
        sessionId: Long? = null,
        payload: DiagnosticPayload = DiagnosticPayload.None,
    ): DiagnosticEvent {
        val event = DiagnosticEvent(
            eventId = nextEventId.getAndIncrement(),
            monotonicNanos = SystemClock.elapsedRealtimeNanos(),
            wallClockMillis = System.currentTimeMillis(),
            severity = severity,
            domain = domain,
            code = code,
            correlationId = correlationId,
            sessionId = sessionId,
            payload = payload,
        )
        ring.add(event)
        metrics.increment(DiagnosticMetricId.DIAGNOSTIC_EVENT_COUNT)
        metrics.observe(DiagnosticMetricId.DIAGNOSTIC_RING_SIZE, ring.size().toDouble())
        return event
    }

    fun observeMetric(metric: DiagnosticMetricId, value: Double) {
        metrics.observe(metric, value)
    }

    fun incrementMetric(metric: DiagnosticMetricId, delta: Double = 1.0) {
        metrics.increment(metric, delta)
    }

    fun events(): List<DiagnosticEvent> = ring.snapshot()

    fun metricSummaries(): List<DiagnosticMetricSummary> = metrics.snapshot()

    fun lifecycleOwners(): List<DiagnosticLifecycleOwner> = lifecycle.snapshot()

    fun captures(): List<DiagnosticCapture> = synchronized(captures) { captures.toList().asReversed() }

    fun activeCapture(): ActiveDiagnosticCapture? = activeCapture?.let { builder ->
        ActiveDiagnosticCapture(
            captureId = builder.captureId,
            mode = builder.mode,
            startedAtNanos = builder.startedAtNanos,
            triggerAtNanos = builder.triggerAtNanos,
            markerCount = builder.markers.size,
        )
    }

    @Synchronized
    fun startRecentProblemCapture(): ActiveDiagnosticCapture {
        check(activeCapture == null) { "A diagnostic capture is already active." }
        val now = SystemClock.elapsedRealtimeNanos()
        val captureId = nextCaptureId.getAndIncrement()
        val preTrigger = ring.snapshot()
        val builder = CaptureBuilder(
            captureId = captureId,
            mode = DiagnosticCaptureMode.RECENT_PROBLEM,
            startedAtNanos = preTrigger.firstOrNull()?.monotonicNanos ?: now,
            triggerAtNanos = now,
            firstLiveEventId = ring.newestEventId(),
            preTriggerEvents = preTrigger,
            markers = mutableListOf(),
        )
        activeCapture = builder
        record(
            severity = DiagnosticSeverity.INFO,
            domain = DiagnosticDomain.DIAGNOSTIC_CAPTURE,
            code = DiagnosticEventCode.CAPTURE_STARTED,
            correlationId = "capture-$captureId",
            payload = DiagnosticPayload.CaptureState(
                captureId = captureId,
                mode = builder.mode,
                markerCount = 0,
            ),
        )
        return activeCapture()!!
    }

    @Synchronized
    fun startReproductionCapture(): ActiveDiagnosticCapture {
        check(activeCapture == null) { "A diagnostic capture is already active." }
        val now = SystemClock.elapsedRealtimeNanos()
        val captureId = nextCaptureId.getAndIncrement()
        val builder = CaptureBuilder(
            captureId = captureId,
            mode = DiagnosticCaptureMode.REPRODUCTION,
            startedAtNanos = now,
            triggerAtNanos = null,
            firstLiveEventId = ring.newestEventId(),
            preTriggerEvents = emptyList(),
            markers = mutableListOf(),
        )
        activeCapture = builder
        record(
            severity = DiagnosticSeverity.INFO,
            domain = DiagnosticDomain.DIAGNOSTIC_CAPTURE,
            code = DiagnosticEventCode.CAPTURE_STARTED,
            correlationId = "capture-$captureId",
            payload = DiagnosticPayload.CaptureState(
                captureId = captureId,
                mode = builder.mode,
                markerCount = 0,
            ),
        )
        return activeCapture()!!
    }

    @Synchronized
    fun addMarker(label: String? = null): DiagnosticMarker {
        val builder = checkNotNull(activeCapture) { "No diagnostic capture is active." }
        val marker = DiagnosticMarker(
            markerId = nextMarkerId.getAndIncrement(),
            monotonicNanos = SystemClock.elapsedRealtimeNanos(),
            wallClockMillis = System.currentTimeMillis(),
            label = label?.trim()?.takeIf { it.isNotEmpty() },
        )
        builder.markers += marker
        record(
            severity = DiagnosticSeverity.INFO,
            domain = DiagnosticDomain.DIAGNOSTIC_CAPTURE,
            code = DiagnosticEventCode.CAPTURE_MARKER_ADDED,
            correlationId = "capture-${builder.captureId}",
            payload = DiagnosticPayload.CaptureState(
                captureId = builder.captureId,
                mode = builder.mode,
                markerCount = builder.markers.size,
            ),
        )
        return marker
    }

    @Synchronized
    fun finishCapture(note: String? = null): DiagnosticCapture {
        val builder = checkNotNull(activeCapture) { "No diagnostic capture is active." }
        val stoppedAt = SystemClock.elapsedRealtimeNanos()
        val liveEvents = ring.snapshotSince(builder.firstLiveEventId)
        val combinedEvents = (builder.preTriggerEvents + liveEvents)
            .distinctBy(DiagnosticEvent::eventId)
            .sortedBy(DiagnosticEvent::eventId)

        val capture = DiagnosticCapture(
            captureId = builder.captureId,
            mode = builder.mode,
            startedAtNanos = builder.startedAtNanos,
            triggerAtNanos = builder.triggerAtNanos,
            stoppedAtNanos = stoppedAt,
            createdAtWallClockMillis = System.currentTimeMillis(),
            events = combinedEvents,
            metrics = metrics.snapshot(),
            lifecycleOwners = lifecycle.snapshot(),
            markers = builder.markers.toList(),
            note = note?.trim()?.takeIf { it.isNotEmpty() },
        )
        activeCapture = null
        synchronized(captures) {
            if (captures.size == retainedCaptureCount) {
                captures.removeFirst()
            }
            captures.addLast(capture)
        }
        metrics.increment(DiagnosticMetricId.DIAGNOSTIC_CAPTURE_COUNT)
        record(
            severity = DiagnosticSeverity.INFO,
            domain = DiagnosticDomain.DIAGNOSTIC_CAPTURE,
            code = DiagnosticEventCode.CAPTURE_FINISHED,
            correlationId = "capture-${capture.captureId}",
            payload = DiagnosticPayload.CaptureState(
                captureId = capture.captureId,
                mode = capture.mode,
                markerCount = capture.markers.size,
            ),
        )
        return capture
    }

    fun createLifecycleOwner(
        ownerId: String,
        ownerKind: String,
        generation: Long,
        initialState: String,
    ): DiagnosticLifecycleOwner {
        val now = SystemClock.elapsedRealtimeNanos()
        val owner = lifecycle.create(ownerId, ownerKind, generation, now, initialState)
        record(
            severity = DiagnosticSeverity.INFO,
            domain = DiagnosticDomain.LIFECYCLE,
            code = DiagnosticEventCode.LIFECYCLE_OWNER_CREATED,
            correlationId = ownerId,
            payload = owner.asPayload(),
        )
        return owner
    }

    fun updateLifecycleOwner(ownerId: String, state: String): DiagnosticLifecycleOwner? {
        val owner = lifecycle.updateState(ownerId, state) ?: return null
        val late = owner.destroyedAtNanos != null
        record(
            severity = if (late) DiagnosticSeverity.WARNING else DiagnosticSeverity.TRACE,
            domain = DiagnosticDomain.LIFECYCLE,
            code = if (late) {
                DiagnosticEventCode.LATE_EVENT_AFTER_OWNER_DESTROYED
            } else {
                DiagnosticEventCode.LIFECYCLE_OWNER_STATE_CHANGED
            },
            correlationId = ownerId,
            payload = owner.asPayload(),
        )
        return owner
    }

    fun destroyLifecycleOwner(ownerId: String): DiagnosticLifecycleOwner? {
        val owner = lifecycle.destroy(ownerId, SystemClock.elapsedRealtimeNanos()) ?: return null
        record(
            severity = DiagnosticSeverity.INFO,
            domain = DiagnosticDomain.LIFECYCLE,
            code = DiagnosticEventCode.LIFECYCLE_OWNER_DESTROYED,
            correlationId = ownerId,
            payload = owner.asPayload(),
        )
        return owner
    }

    fun invariantViolation(
        invariantId: String,
        detail: String,
        domain: DiagnosticDomain,
        correlationId: String? = null,
    ) {
        record(
            severity = DiagnosticSeverity.ERROR,
            domain = domain,
            code = DiagnosticEventCode.INVARIANT_VIOLATION,
            correlationId = correlationId,
            payload = DiagnosticPayload.InvariantViolation(invariantId, detail),
        )
    }

    private fun DiagnosticLifecycleOwner.asPayload() = DiagnosticPayload.LifecycleState(
        ownerId = ownerId,
        ownerKind = ownerKind,
        state = currentState,
        generation = generation,
    )
}
