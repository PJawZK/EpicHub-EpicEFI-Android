package com.epicefi.epichub.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticCoreTest {
    @Test
    fun ringEvictsOldestPreservesOrderAndCountsDrops() {
        val ring = DiagnosticRing(capacity = 3)
        repeat(4) { index ->
            ring.add(testEvent(eventId = (index + 1).toLong()))
        }

        assertEquals(listOf(2L, 3L, 4L), ring.snapshot().map { it.eventId })
        assertEquals(listOf(3L, 4L), ring.snapshotSince(2).map { it.eventId })
        assertEquals(4L, ring.newestEventId())
        assertEquals(1L, ring.droppedTotal())
    }

    @Test
    fun metricsExposeCurrentRangeAndPercentiles() {
        val metrics = DiagnosticMetrics(distributionCapacity = 128)
        for (value in 1..100) {
            metrics.observe(DiagnosticMetricId.RUNTIME_SNAPSHOT_READ_MS, value.toDouble())
        }

        val summary = metrics.snapshot().first {
            it.metric == DiagnosticMetricId.RUNTIME_SNAPSHOT_READ_MS
        }

        assertEquals(100L, summary.sampleCount)
        assertEquals(100.0, summary.current, 0.0)
        assertEquals(1.0, summary.minimum, 0.0)
        assertEquals(100.0, summary.maximum, 0.0)
        assertEquals(50.0, summary.p50, 0.0)
        assertEquals(95.0, summary.p95, 0.0)
        assertEquals(99.0, summary.p99, 0.0)
    }

    @Test
    fun hubExposesDroppedEventMetricWhenRingOverflows() {
        val clock = FakeDiagnosticClock(monotonic = 0, wall = 0)
        val hub = DiagnosticHub(eventCapacity = 2, clock = clock)
        repeat(3) {
            hub.record(
                severity = DiagnosticSeverity.INFO,
                domain = DiagnosticDomain.APP_UI,
                code = DiagnosticEventCode.APP_STARTED,
            )
            clock.advance(1)
        }

        val dropped = hub.metricSummaries().first {
            it.metric == DiagnosticMetricId.DIAGNOSTIC_DROPPED_EVENT_COUNT
        }
        assertEquals(1.0, dropped.current, 0.0)
    }

    @Test
    fun recentProblemCaptureIncludesPreTriggerMarkersAndFinishEvent() {
        val clock = FakeDiagnosticClock(monotonic = 100, wall = 1_000)
        val hub = DiagnosticHub(
            eventCapacity = 32,
            retainedCaptureCount = 3,
            clock = clock,
        )
        hub.record(
            severity = DiagnosticSeverity.INFO,
            domain = DiagnosticDomain.APP_UI,
            code = DiagnosticEventCode.APP_STARTED,
        )

        clock.advance(10)
        val active = hub.startRecentProblemCapture()
        assertEquals(110L, active.triggerAtNanos)

        clock.advance(10)
        hub.addMarker("problem visible")

        clock.advance(10)
        val capture = hub.finishCapture("test note")

        assertNull(hub.activeCapture())
        assertEquals(DiagnosticCaptureMode.RECENT_PROBLEM, capture.mode)
        assertEquals(100L, capture.startedAtNanos)
        assertEquals(110L, capture.triggerAtNanos)
        assertEquals(130L, capture.stoppedAtNanos)
        assertEquals("problem visible", capture.markers.single().label)
        assertEquals("test note", capture.note)
        assertTrue(capture.events.any { it.code == DiagnosticEventCode.APP_STARTED })
        assertTrue(capture.events.any { it.code == DiagnosticEventCode.CAPTURE_STARTED })
        assertTrue(capture.events.any { it.code == DiagnosticEventCode.CAPTURE_MARKER_ADDED })
        assertTrue(capture.events.any { it.code == DiagnosticEventCode.CAPTURE_FINISHED })
        assertEquals(1, hub.captures().size)

        val captureMetric = capture.metrics.first {
            it.metric == DiagnosticMetricId.DIAGNOSTIC_CAPTURE_COUNT
        }
        assertEquals(1.0, captureMetric.current, 0.0)
    }

    @Test
    fun recentProblemCaptureFinishesOnlyAfterPostTriggerDeadline() {
        val clock = FakeDiagnosticClock(monotonic = 1_000, wall = 10_000)
        val hub = DiagnosticHub(clock = clock)
        hub.startRecentProblemCapture()

        clock.advance(2_999)
        assertNull(hub.finishRecentProblemCaptureIfDue(postTriggerNanos = 3_000))
        assertTrue(hub.activeCapture() != null)

        clock.advance(1)
        val capture = hub.finishRecentProblemCaptureIfDue(postTriggerNanos = 3_000)

        assertTrue(capture != null)
        assertNull(hub.activeCapture())
        assertEquals(1, hub.captures().size)
    }

    @Test
    fun reproductionCaptureDoesNotAutoFinishThroughRecentProblemDeadline() {
        val clock = FakeDiagnosticClock(monotonic = 10, wall = 100)
        val hub = DiagnosticHub(clock = clock)
        hub.startReproductionCapture()

        clock.advance(10_000)

        assertNull(hub.finishRecentProblemCaptureIfDue(postTriggerNanos = 1))
        assertTrue(hub.activeCapture() != null)
    }

    @Test
    fun reproductionCaptureDoesNotPullEarlierEventsIntoEvidence() {
        val clock = FakeDiagnosticClock(monotonic = 10, wall = 100)
        val hub = DiagnosticHub(clock = clock)
        hub.record(
            severity = DiagnosticSeverity.INFO,
            domain = DiagnosticDomain.APP_UI,
            code = DiagnosticEventCode.APP_STARTED,
        )

        clock.advance(5)
        hub.startReproductionCapture()
        clock.advance(5)
        val capture = hub.finishCapture()

        assertFalse(capture.events.any { it.code == DiagnosticEventCode.APP_STARTED })
        assertTrue(capture.events.any { it.code == DiagnosticEventCode.CAPTURE_STARTED })
        assertTrue(capture.events.any { it.code == DiagnosticEventCode.CAPTURE_FINISHED })
    }

    private fun testEvent(eventId: Long) = DiagnosticEvent(
        eventId = eventId,
        monotonicNanos = eventId,
        wallClockMillis = eventId,
        severity = DiagnosticSeverity.INFO,
        domain = DiagnosticDomain.APP_UI,
        code = DiagnosticEventCode.APP_STARTED,
        correlationId = null,
        sessionId = null,
        payload = DiagnosticPayload.None,
    )

    private class FakeDiagnosticClock(
        private var monotonic: Long,
        private var wall: Long,
    ) : DiagnosticClock {
        override fun monotonicNanos(): Long = monotonic

        override fun wallClockMillis(): Long = wall

        fun advance(delta: Long) {
            monotonic += delta
            wall += delta
        }
    }
}
