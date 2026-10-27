package com.epicefi.epichub.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRuntimeSamplingGateTest {
    private fun frame(
        epoch: Long = 7L,
        sequence: Long = 1L,
        timestamp: Long = 100L,
        generation: Long = 25L,
        revision: Long = 88L,
    ) = LiveRuntimeFrame(
        epoch = epoch,
        sequence = sequence,
        receivedAtMs = timestamp,
        profileGeneration = generation,
        definitionRevision = revision,
        readings = listOf(LiveRuntimeReading(1001L, 801f), LiveRuntimeReading(1002L, 14.1f)),
    )

    @Test
    fun freshUnknownStaleProgressionRequiresNoTimerAndNoAdditionalUsbOwner() {
        val gate = LiveRuntimeSamplingGate(LiveRuntimeSamplingPolicy(50, 200, 75))
        assertEquals(LiveRuntimeValidity.Unknown, gate.snapshot(100).validity)
        assertFalse(gate.tryBegin(100))
        gate.bind(7)
        assertEquals(LiveRuntimeValidity.Unknown, gate.snapshot(100).validity)
        assertTrue(gate.tryBegin(100))
        assertTrue(gate.accept(7, frame(), 110))
        val fresh = gate.snapshot(299)
        assertEquals(LiveRuntimeValidity.Fresh, fresh.validity)
        assertEquals(199L, fresh.ageMs)
        assertTrue(fresh.canUseAsLive)
        val stale = gate.snapshot(300)
        assertEquals(LiveRuntimeValidity.Stale, stale.validity)
        assertFalse(stale.canUseAsLive)
        assertEquals(801f, stale.frame!!.readings[0].value, 0.001f)
        assertEquals(75, gate.timeoutMs)
    }

    @Test
    fun intervalEnforcesAtMostOneRequestPerDueWindowIncludingAtBoundary() {
        val gate = LiveRuntimeSamplingGate(LiveRuntimeSamplingPolicy(40, 150, 50))
        gate.bind(5)
        assertTrue(gate.tryBegin(1_000))
        assertFalse(gate.tryBegin(1_000))
        assertFalse(gate.tryBegin(1_039))
        assertTrue(gate.tryBegin(1_040))
        assertFalse(gate.tryBegin(1_041))
    }

    @Test
    fun badSequenceEpochClockAndDefinitionNeverRestoreFresh() {
        val gate = LiveRuntimeSamplingGate()
        gate.bind(7)
        assertTrue(gate.accept(7, frame(), 101))
        assertFalse(gate.accept(7, frame(sequence = 1), 102))
        assertEquals(LiveRuntimeValidity.Invalid, gate.snapshot(103).validity)
        assertFalse(gate.snapshot(103).canUseAsLive)
        assertFalse(gate.tryBegin(200))
        gate.bind(7)
        assertTrue(gate.accept(7, frame(), 101))
        assertFalse(gate.accept(7, frame(sequence = 2, revision = 89), 151))
        assertEquals(LiveRuntimeValidity.Invalid, gate.snapshot(151).validity)
        gate.bind(7)
        assertFalse(gate.accept(7, frame(epoch = 8), 110))
        gate.bind(7)
        assertFalse(gate.accept(7, frame(), 99))
        gate.bind(7)
        assertTrue(gate.tryBegin(100))
        assertFalse(gate.tryBegin(99))
        assertEquals(LiveRuntimeValidity.Invalid, gate.snapshot(110).validity)
    }

    @Test
    fun ageAndInvalidityNeverBecomeFreshWithoutNewValidatedData() {
        val gate = LiveRuntimeSamplingGate(LiveRuntimeSamplingPolicy(20, 80, 50))
        gate.bind(7)
        assertTrue(gate.accept(7, frame(), 105))
        assertEquals(LiveRuntimeValidity.Stale, gate.snapshot(180).validity)
        assertEquals(LiveRuntimeValidity.Stale, gate.snapshot(300).validity)
        gate.fail("CRC mismatch")
        assertEquals(LiveRuntimeValidity.Invalid, gate.snapshot(300).validity)
        assertFalse(gate.tryBegin(301))
        gate.disconnect()
        assertEquals(LiveRuntimeValidity.Invalid, gate.snapshot(301).validity)
    }

    @Test
    fun reconnectDiscardsAllPreviousEcuChannelNumbersAndSessionAuthority() {
        val gate = LiveRuntimeSamplingGate()
        gate.bind(7)
        assertTrue(gate.accept(7, frame(), 102))
        gate.disconnect()
        val history = gate.snapshot(103)
        assertEquals(LiveRuntimeValidity.Stale, history.validity)
        assertNull(history.epoch)
        assertFalse(history.canUseAsLive)
        gate.bind(9)
        val next = gate.snapshot(104)
        assertEquals(LiveRuntimeValidity.Unknown, next.validity)
        assertNull(next.frame)
        assertFalse(gate.accept(7, frame(), 105))
        assertEquals(LiveRuntimeValidity.Invalid, gate.snapshot(106).validity)
        gate.bind(9)
        assertTrue(gate.accept(9, frame(epoch = 9), 110))
        assertTrue(gate.snapshot(111).canUseAsLive)
    }

    @Test
    fun malformedAndDuplicateChannelsFailClosed() {
        val gate = LiveRuntimeSamplingGate()
        gate.bind(7)
        val bad = frame().copy(readings = listOf(LiveRuntimeReading(9, Float.NaN)))
        assertFalse(gate.accept(7, bad, 101))
        gate.bind(7)
        val duplicate = frame().copy(readings = listOf(LiveRuntimeReading(9, 1f), LiveRuntimeReading(9, 2f)))
        assertFalse(gate.accept(7, duplicate, 101))
        gate.bind(7)
        assertFalse(gate.accept(7, frame().copy(readings = emptyList()), 101))
        gate.bind(7)
        assertFalse(gate.accept(7, frame().copy(profileGeneration = 0), 101))
        gate.bind(7)
        assertFalse(gate.accept(7, frame(), 500))
        assertFalse(gate.snapshot(501).canUseAsLive)
    }

    @Test
    fun clockRegressionOnReadSideExplicitlyDeniesFresh() {
        val gate = LiveRuntimeSamplingGate()
        gate.bind(7)
        assertTrue(gate.accept(7, frame(), 101))
        val invalid = gate.snapshot(99)
        assertEquals(LiveRuntimeValidity.Invalid, invalid.validity)
        assertFalse(invalid.canUseAsLive)
    }

    @Test
    fun suppliedListsCannotMutateRetainedChannelBatchAcrossReads() {
        val gate = LiveRuntimeSamplingGate()
        val original = mutableListOf(LiveRuntimeReading(1, 10f))
        gate.bind(7)
        assertTrue(gate.accept(7, frame().copy(readings = original), 102))
        original.clear()
        assertEquals(1, gate.snapshot(103).frame!!.readings.size)
    }
}
