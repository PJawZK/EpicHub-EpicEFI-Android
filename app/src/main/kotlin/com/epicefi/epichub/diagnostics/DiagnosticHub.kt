package com.epicefi.epichub.diagnostics

import java.util.concurrent.atomic.AtomicLong

class DiagnosticHub(
    eventCapacity: Int = 512,
    private val retainedCaptureCount: Int = 20,
    private val recentWorstFrameCapacity: Int = 32,
    private val capturedWorstFrameCount: Int = 16,
    private val clock: DiagnosticClock = SystemDiagnosticClock,
) {
    init {
        require(retainedCaptureCount > 0) { "Retained capture count must be positive." }
        require(recentWorstFrameCapacity > 0) { "Recent worst-frame capacity must be positive." }
        require(capturedWorstFrameCount > 0) { "Captured worst-frame count must be positive." }
    }

    private data class CaptureBuilder(
        val captureId: Long,
        val mode: DiagnosticCaptureMode,
        val startedAtNanos: Long,
        val triggerAtNanos: Long?,
        val firstLiveEventId: Long,
        val preTriggerEvents: List<DiagnosticEvent>,
        val markers: MutableList<DiagnosticMarker>,
        val worstFrames: MutableList<DiagnosticWorstFrame>,
    )

    private data class PresentationContext(
        val destinationId: String,
        val changedAtNanos: Long,
    )

    private val nextEventId = AtomicLong(1)
    private val nextCaptureId = AtomicLong(1)
    private val nextMarkerId = AtomicLong(1)
    private val ring = DiagnosticRing(eventCapacity)
    private val metrics = DiagnosticMetrics()
    private val lifecycle = DiagnosticLifecycleRegistry()
    private val captures = ArrayDeque<DiagnosticCapture>()
    private val recentWorstFrames = ArrayDeque<DiagnosticWorstFrame>()

    @Volatile
    private var activeCapture: CaptureBuilder? = null

    @Volatile
    private var activeCaptureMetrics: DiagnosticMetrics? = null

    @Volatile
    private var presentationContext = PresentationContext(
        destinationId = "dashboard",
        changedAtNanos = clock.monotonicNanos(),
    )

    fun setCurrentDestination(destinationId: String) {
        val current = presentationContext
        if (current.destinationId == destinationId) return
        presentationContext = PresentationContext(
            destinationId = destinationId,
            changedAtNanos = clock.monotonicNanos(),
        )
    }

    fun currentDestination(): String = presentationContext.destinationId

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
            monotonicNanos = clock.monotonicNanos(),
            wallClockMillis = clock.wallClockMillis(),
            severity = severity,
            domain = domain,
            code = code,
            correlationId = correlationId,
            sessionId = sessionId,
            payload = payload,
        )
        val dropped = ring.add(event)
        incrementMetricInternal(DiagnosticMetricId.DIAGNOSTIC_EVENT_COUNT)
        if (dropped) {
            incrementMetricInternal(DiagnosticMetricId.DIAGNOSTIC_DROPPED_EVENT_COUNT)
        }
        observeMetricInternal(DiagnosticMetricId.DIAGNOSTIC_RING_SIZE, ring.size().toDouble())
        return event
    }

    fun observeMetric(metric: DiagnosticMetricId, value: Double) {
        observeMetricInternal(metric, value)
    }

    fun incrementMetric(metric: DiagnosticMetricId, delta: Double = 1.0) {
        incrementMetricInternal(metric, delta)
    }

    @Synchronized
    fun recordWorstFrame(
        frameNumber: Long,
        firstDrawFrame: Boolean,
        totalMs: Double,
        budgetMs: Double,
        overrunMs: Double,
        vsyncDelayMs: Double?,
        unknownDelayMs: Double?,
        animationMs: Double?,
        layoutMeasureMs: Double?,
        drawMs: Double?,
        syncMs: Double?,
        commandIssueMs: Double?,
        swapBuffersMs: Double?,
        gpuMs: Double?,
    ): DiagnosticWorstFrame {
        val context = presentationContext
        val monotonicNow = clock.monotonicNanos()
        val contextAgeMs = (monotonicNow - context.changedAtNanos)
            .coerceAtLeast(0L) / NANOS_PER_MILLISECOND
        val frame = DiagnosticWorstFrame(
            frameNumber = frameNumber,
            monotonicNanos = monotonicNow,
            wallClockMillis = clock.wallClockMillis(),
            destinationId = context.destinationId,
            contextAgeMs = contextAgeMs,
            firstDrawFrame = firstDrawFrame,
            totalMs = totalMs,
            budgetMs = budgetMs,
            overrunMs = overrunMs,
            vsyncDelayMs = vsyncDelayMs,
            unknownDelayMs = unknownDelayMs,
            animationMs = animationMs,
            layoutMeasureMs = layoutMeasureMs,
            drawMs = drawMs,
            syncMs = syncMs,
            commandIssueMs = commandIssueMs,
            swapBuffersMs = swapBuffersMs,
            gpuMs = gpuMs,
        )

        if (firstDrawFrame) {
            incrementMetricInternal(DiagnosticMetricId.UI_JANK_FIRST_DRAW_FRAME_COUNT)
        }
        if (contextAgeMs <= CONTEXT_TRANSITION_WINDOW_MS) {
            incrementMetricInternal(DiagnosticMetricId.UI_JANK_WITHIN_500MS_OF_CONTEXT_CHANGE_COUNT)
        } else {
            incrementMetricInternal(DiagnosticMetricId.UI_JANK_STEADY_STATE_COUNT)
        }

        if (recentWorstFrames.size == recentWorstFrameCapacity) {
            recentWorstFrames.removeFirst()
        }
        recentWorstFrames.addLast(frame)
        activeCapture?.let { builder ->
            retainWorstFrame(builder.worstFrames, frame)
        }
        return frame
    }

    fun events(): List<DiagnosticEvent> = ring.snapshot()

    fun eventCount(): Int = ring.size()

    fun metricSummaries(): List<DiagnosticMetricSummary> = metrics.snapshot()

    fun metricSummaries(metricIds: Collection<DiagnosticMetricId>): List<DiagnosticMetricSummary> =
        metrics.snapshot(metricIds)

    fun lifecycleOwners(): List<DiagnosticLifecycleOwner> = lifecycle.snapshot()

    fun lifecycleOwnerCount(): Int = lifecycle.size()

    fun captures(): List<DiagnosticCapture> = synchronized(captures) { captures.toList().asReversed() }

    fun captureCount(): Int = synchronized(captures) { captures.size }

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
        val now = clock.monotonicNanos()
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
            worstFrames = recentWorstFrames
                .sortedByDescending(DiagnosticWorstFrame::overrunMs)
                .take(capturedWorstFrameCount)
                .toMutableList(),
        )
        activeCaptureMetrics = DiagnosticMetrics()
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
    fun finishRecentProblemCaptureIfDue(postTriggerNanos: Long): DiagnosticCapture? {
        require(postTriggerNanos >= 0) { "Post-trigger duration must not be negative." }
        val builder = activeCapture ?: return null
        if (builder.mode != DiagnosticCaptureMode.RECENT_PROBLEM) {
            return null
        }
        val triggerAt = builder.triggerAtNanos ?: return null
        if (clock.monotonicNanos() - triggerAt < postTriggerNanos) {
            return null
        }
        return finishCapture()
    }

    @Synchronized
    fun startReproductionCapture(): ActiveDiagnosticCapture {
        check(activeCapture == null) { "A diagnostic capture is already active." }
        val now = clock.monotonicNanos()
        val captureId = nextCaptureId.getAndIncrement()
        val builder = CaptureBuilder(
            captureId = captureId,
            mode = DiagnosticCaptureMode.REPRODUCTION,
            startedAtNanos = now,
            triggerAtNanos = null,
            firstLiveEventId = ring.newestEventId(),
            preTriggerEvents = emptyList(),
            markers = mutableListOf(),
            worstFrames = mutableListOf(),
        )
        activeCaptureMetrics = DiagnosticMetrics()
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
            monotonicNanos = clock.monotonicNanos(),
            wallClockMillis = clock.wallClockMillis(),
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
        val stoppedAt = clock.monotonicNanos()

        incrementMetricInternal(DiagnosticMetricId.DIAGNOSTIC_CAPTURE_COUNT)
        record(
            severity = DiagnosticSeverity.INFO,
            domain = DiagnosticDomain.DIAGNOSTIC_CAPTURE,
            code = DiagnosticEventCode.CAPTURE_FINISHED,
            correlationId = "capture-${builder.captureId}",
            payload = DiagnosticPayload.CaptureState(
                captureId = builder.captureId,
                mode = builder.mode,
                markerCount = builder.markers.size,
            ),
        )

        val liveEvents = ring.snapshotSince(builder.firstLiveEventId)
        val combinedEvents = (builder.preTriggerEvents + liveEvents)
            .distinctBy(DiagnosticEvent::eventId)
            .sortedBy(DiagnosticEvent::eventId)
        val captureMetricWindow = activeCaptureMetrics
        activeCaptureMetrics = null

        val capture = DiagnosticCapture(
            captureId = builder.captureId,
            mode = builder.mode,
            startedAtNanos = builder.startedAtNanos,
            triggerAtNanos = builder.triggerAtNanos,
            stoppedAtNanos = stoppedAt,
            createdAtWallClockMillis = clock.wallClockMillis(),
            events = combinedEvents,
            metrics = captureMetricWindow?.snapshot() ?: emptyList(),
            lifecycleOwners = lifecycle.snapshot(),
            markers = builder.markers.toList(),
            worstFrames = builder.worstFrames.sortedByDescending(DiagnosticWorstFrame::overrunMs),
            note = note?.trim()?.takeIf { it.isNotEmpty() },
        )
        activeCapture = null
        synchronized(captures) {
            if (captures.size == retainedCaptureCount) {
                captures.removeFirst()
            }
            captures.addLast(capture)
        }
        return capture
    }

    fun createLifecycleOwner(
        ownerId: String,
        ownerKind: String,
        generation: Long,
        initialState: String,
    ): DiagnosticLifecycleOwner {
        val now = clock.monotonicNanos()
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
        val owner = lifecycle.destroy(ownerId, clock.monotonicNanos()) ?: return null
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

    private fun observeMetricInternal(metric: DiagnosticMetricId, value: Double) {
        metrics.observe(metric, value)
        activeCaptureMetrics?.observe(metric, value)
    }

    private fun incrementMetricInternal(metric: DiagnosticMetricId, delta: Double = 1.0) {
        metrics.increment(metric, delta)
        activeCaptureMetrics?.increment(metric, delta)
    }

    private fun retainWorstFrame(
        frames: MutableList<DiagnosticWorstFrame>,
        frame: DiagnosticWorstFrame,
    ) {
        frames += frame
        if (frames.size > capturedWorstFrameCount) {
            val leastBad = frames.indices.minByOrNull { frames[it].overrunMs }
            if (leastBad != null) {
                frames.removeAt(leastBad)
            }
        }
    }

    private fun DiagnosticLifecycleOwner.asPayload() = DiagnosticPayload.LifecycleState(
        ownerId = ownerId,
        ownerKind = ownerKind,
        state = currentState,
        generation = generation,
    )

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000.0
        const val CONTEXT_TRANSITION_WINDOW_MS = 500.0
    }
}
