package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RuntimeSnapshotCodecTest {
    @Test
    fun decodesKnownLittleEndianSnapshotLayout() {
        val buffer = baseSnapshotBuffer()

        val snapshot = RuntimeSnapshotCodec.decode(buffer)

        assertEquals(7L, snapshot.sequence)
        assertEquals(11L, snapshot.monotonicNanos)
        assertEquals(13L, snapshot.sessionId)
        assertEquals(1, snapshot.sourceKind)
        assertEquals(1, snapshot.flags)
        assertEquals(3, snapshot.channels.size)

        assertEquals(0xFFFF_0001L, snapshot.channels[0].id)
        assertEquals(RuntimeChannelValidity.VALID, snapshot.channels[0].validity)
        assertEquals(950.0f, snapshot.channels[0].value)

        assertEquals(0xFFFF_0002L, snapshot.channels[1].id)
        assertEquals(52.0f, snapshot.channels[1].value)

        assertEquals(0xFFFF_0003L, snapshot.channels[2].id)
        assertEquals(14.7f, snapshot.channels[2].value)
    }

    @Test
    fun rejectsUnknownAbiVersion() {
        val buffer = baseSnapshotBuffer().apply {
            putInt(0, RuntimeSnapshotLayout.ABI_VERSION + 1)
        }

        assertThrows(IllegalArgumentException::class.java) {
            RuntimeSnapshotCodec.decode(buffer)
        }
    }

    @Test
    fun rejectsUnexpectedSnapshotSize() {
        val buffer = baseSnapshotBuffer().apply {
            putInt(4, RuntimeSnapshotLayout.BYTE_SIZE - 4)
        }

        assertThrows(IllegalArgumentException::class.java) {
            RuntimeSnapshotCodec.decode(buffer)
        }
    }

    @Test
    fun rejectsChannelCountOutsideFixedAbiCapacity() {
        val buffer = baseSnapshotBuffer().apply {
            putInt(40, RuntimeSnapshotLayout.MAX_CHANNELS + 1)
        }

        assertThrows(IllegalArgumentException::class.java) {
            RuntimeSnapshotCodec.decode(buffer)
        }
    }

    private fun baseSnapshotBuffer(): ByteBuffer = ByteBuffer
        .allocate(RuntimeSnapshotLayout.BYTE_SIZE)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply {
            putInt(0, RuntimeSnapshotLayout.ABI_VERSION)
            putInt(4, RuntimeSnapshotLayout.BYTE_SIZE)
            putLong(8, 7L)
            putLong(16, 11L)
            putLong(24, 13L)
            putInt(32, 1)
            putInt(36, 1)
            putInt(40, 3)
            putInt(44, 0)

            putInt(48, 0xFFFF_0001L.toInt())
            putInt(52, RuntimeChannelValidity.VALID.code)
            putFloat(56, 950.0f)

            putInt(60, 0xFFFF_0002L.toInt())
            putInt(64, RuntimeChannelValidity.VALID.code)
            putFloat(68, 52.0f)

            putInt(72, 0xFFFF_0003L.toInt())
            putInt(76, RuntimeChannelValidity.VALID.code)
            putFloat(80, 14.7f)
        }
}
