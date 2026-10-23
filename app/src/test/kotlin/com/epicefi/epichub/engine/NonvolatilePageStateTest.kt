package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class NonvolatilePageStateTest {
    private fun snapshot(status: Int, page: Int = 1): ByteBuffer =
        ByteBuffer.allocateDirect(NONVOLATILE_SNAPSHOT_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(byteArrayOf(78, 86, 80, 49))
            put(status.toByte())
            put(1.toByte())
            putShort(0)
            putLong(113)
            putLong(4)
            putLong(3)
            putInt(page)
            putInt(if (status == 0) 0 else 0x12345678)
            putInt(if (status == 0) 0 else 0x23456789)
            putInt(0)
            rewind()
        }

    @Test
    fun unknownDoesNotInventPersistenceEvidence() {
        val state = decodeNonvolatilePageState(snapshot(0), 1)
        assertEquals(NonvolatilePageStatus.Unknown, state.status)
        assertNull(state.expectedPageCrc32)
        assertNull(state.signatureCrc32)
    }

    @Test
    fun verifiedAndStaleRemainDistinctAndPreserveExactPage() {
        val verified = decodeNonvolatilePageState(snapshot(1), 1)
        assertEquals(NonvolatilePageStatus.PostBurnCrcVerified, verified.status)
        assertEquals(0x12345678L, verified.expectedPageCrc32)
        assertEquals(1, verified.page)
        assertEquals(NonvolatilePageStatus.StaleEvidence, decodeNonvolatilePageState(snapshot(2), 1).status)
    }

    @Test
    fun malformedOrWrongPageFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            decodeNonvolatilePageState(snapshot(1, 2), 1)
        }
        assertThrows(IllegalStateException::class.java) {
            decodeNonvolatilePageState(snapshot(3), 1)
        }
        val unknownWithProof = snapshot(0).apply {
            putInt(36, 42)
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeNonvolatilePageState(unknownWithProof, 1)
        }
    }
}
