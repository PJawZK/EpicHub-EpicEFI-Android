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
    fun lifecycleRegistryBoundsDestroyedHistoryWithoutEvictingActiveOwners() {
        val registry = DiagnosticLifecycleRegistry(retainedDestroyedCapacity = 2)
        registry.create("active", "Test", 1, 1, "running")
        repeat(3) { index ->
            val id = "destroyed-${index + 1}"
            registry.create(id, "Test", (index + 2).toLong(), (index + 2).toLong(), "running")
            registry.destroy(id, (index + 10).toLong())
        }

        val owners = registry.snapshot()
        assertTrue(owners.any { it.ownerId == "active" && it.destroyedAtNanos == null })
        assertFalse(owners.any { it.ownerId == "destroyed-1" })
        assertTrue(owners.any { it.ownerId == "destroyed-2" })
        assertTrue(owners.any { it.ownerId == "destroyed-3" })
        assertEquals(3, owners.size)
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
    fun reproductionCaptureMetricsAreScopedToTheCaptureWindow() {
        val clock = FakeDiagnosticClock(monotonic = 100, wall = 1_000)
        val hub = DiagnosticHub(clock = clock)

        hub.observeMetric(DiagnosticMetricId.UI_FRAME_TOTAL_MS, 100.0)
        hub.startReproductionCapture()
        hub.observeMetric(DiagnosticMetricId.UI_FRAME_TOTAL_MS, 10.0)
        hub.observeMetric(DiagnosticMetricId.UI_FRAME_TOTAL_MS, 20.0)
        val capture = hub.finishCapture()

        val captured = capture.metrics.first {
            it.metric == DiagnosticMetricId.UI_FRAME_TOTAL_MS
        }
        val processLifetime = hub.metricSummaries().first {
            it.metric == DiagnosticMetricId.UI_FRAME_TOTAL_MS
        }

        assertEquals(2L, captured.sampleCount)
        assertEquals(10.0, captured.minimum, 0.0)
        assertEquals(20.0, captured.maximum, 0.0)
        assertEquals(3L, processLifetime.sampleCount)
        assertEquals(100.0, processLifetime.maximum, 0.0)
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
