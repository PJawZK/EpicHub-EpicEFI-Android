package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeDiagnosticBatchCodecTest {
    @Test
    fun decodesKnownLittleEndianDiagnosticBatch() {
        val buffer = baseBatchBuffer()

        val batch = NativeDiagnosticBatchCodec.decode(buffer)

        assertEquals(3L, batch.droppedTotal)
        assertEquals(2, batch.events.size)

        val created = batch.events[0]
        assertEquals(1L, created.eventId)
        assertEquals(0L, created.engineElapsedNanos)
        assertEquals(NativeDiagnosticCode.ENGINE_CREATED, created.code)
        assertEquals(NativeDiagnosticSeverity.INFO, created.severity)
        assertEquals(42L, created.arg0)
        assertEquals(0L, created.arg1)

        val runtime = batch.events[1]
        assertEquals(2L, runtime.eventId)
        assertEquals(5_000_000L, runtime.engineElapsedNanos)
        assertEquals(NativeDiagnosticCode.RUNTIME_SNAPSHOT_PUBLISHED, runtime.code)
        assertEquals(NativeDiagnosticSeverity.TRACE, runtime.severity)
        assertEquals(20L, runtime.arg0)
        assertEquals(3L, runtime.arg1)
    }

    @Test
    fun preservesUnknownCodeInsteadOfGuessing() {
        val buffer = baseBatchBuffer().apply {
            putInt(NativeDiagnosticBatchLayout.HEADER_SIZE + 16, 777)
        }

        val event = NativeDiagnosticBatchCodec.decode(buffer).events.first()

        assertEquals(NativeDiagnosticCode.UNKNOWN, event.code)
        assertEquals(777, event.rawCode)
    }

    @Test
    fun rejectsUnknownAbiVersion() {
        val buffer = baseBatchBuffer().apply {
            putInt(0, NativeDiagnosticBatchLayout.ABI_VERSION + 1)
        }

        assertThrows(IllegalArgumentException::class.java) {
            NativeDiagnosticBatchCodec.decode(buffer)
        }
    }

    @Test
    fun rejectsUnexpectedByteSize() {
        val buffer = baseBatchBuffer().apply {
            putInt(4, NativeDiagnosticBatchLayout.BYTE_SIZE - 1)
        }

        assertThrows(IllegalArgumentException::class.java) {
            NativeDiagnosticBatchCodec.decode(buffer)
        }
    }

    @Test
    fun rejectsEventCountAboveBatchCapacity() {
        val buffer = baseBatchBuffer().apply {
            putInt(8, NativeDiagnosticBatchLayout.MAX_EVENTS + 1)
        }

        assertThrows(IllegalArgumentException::class.java) {
            NativeDiagnosticBatchCodec.decode(buffer)
        }
    }

    private fun baseBatchBuffer(): ByteBuffer = ByteBuffer
        .allocate(NativeDiagnosticBatchLayout.BYTE_SIZE)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply {
            putInt(0, NativeDiagnosticBatchLayout.ABI_VERSION)
            putInt(4, NativeDiagnosticBatchLayout.BYTE_SIZE)
            putInt(8, 2)
            putInt(12, 3)

            val first = NativeDiagnosticBatchLayout.HEADER_SIZE
            putLong(first, 1L)
            putLong(first + 8, 0L)
            putInt(first + 16, NativeDiagnosticCode.ENGINE_CREATED.code)
            putInt(first + 20, NativeDiagnosticSeverity.INFO.code)
            putLong(first + 24, 42L)
            putLong(first + 32, 0L)

            val second = first + NativeDiagnosticBatchLayout.EVENT_SIZE
            putLong(second, 2L)
            putLong(second + 8, 5_000_000L)
            putInt(second + 16, NativeDiagnosticCode.RUNTIME_SNAPSHOT_PUBLISHED.code)
            putInt(second + 20, NativeDiagnosticSeverity.TRACE.code)
            putLong(second + 24, 20L)
            putLong(second + 32, 3L)
        }
}
