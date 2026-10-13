package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerTableProjectionDecoderTest {
    @Test
    fun decodesSelfDescribingVariableTableProjection() {
        val projection = TunerTableProjectionDecoder.decode(baseProjectionBuffer())

        assertEquals(42L, projection.profileGeneration)
        assertEquals(3L, projection.draftRevision)
        assertEquals(7, projection.tableId)
        assertEquals("VE", projection.name)
        assertEquals(2, projection.rows)
        assertEquals(3, projection.cols)
        assertEquals(2, projection.displayDecimals)
        assertTrue(projection.isOffline)
        assertTrue(projection.isFixture)
        assertArrayEquals(doubleArrayOf(1000.0, 2000.0, 3000.0), projection.xAxis, 0.0)
        assertArrayEquals(doubleArrayOf(40.0, 100.0), projection.yAxis, 0.0)
        assertArrayEquals(doubleArrayOf(50.0, 55.0, 60.0, 70.0, 75.0, 80.0), projection.values, 0.0)
        assertFalse(projection.editable[4])
    }

    @Test
    fun rejectsUnknownAbi() {
        val buffer = baseProjectionBuffer().apply { putInt(0, 99) }
        assertThrows(IllegalArgumentException::class.java) {
            TunerTableProjectionDecoder.decode(buffer)
        }
    }

    @Test
    fun rejectsOutOfBoundsSection() {
        val buffer = baseProjectionBuffer().apply { putInt(104, capacity() - 4) }
        assertThrows(IllegalArgumentException::class.java) {
            TunerTableProjectionDecoder.decode(buffer)
        }
    }

    private fun baseProjectionBuffer(): ByteBuffer {
        val totalSize = 112 + 8 + (3 + 2 + 6) * Double.SIZE_BYTES + 6
        val nameOffset = 112
        val xOffset = 120
        val yOffset = xOffset + 3 * Double.SIZE_BYTES
        val valuesOffset = yOffset + 2 * Double.SIZE_BYTES
        val editableOffset = valuesOffset + 6 * Double.SIZE_BYTES
        return ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0, 1)
            putInt(4, totalSize)
            putLong(8, 42L)
            putLong(16, 3L)
            putInt(24, 1)
            putInt(28, 7)
            putInt(32, 2)
            putInt(36, 3)
            putInt(40, 2)
            putInt(44, TABLE_PROJECTION_FLAG_OFFLINE or TABLE_PROJECTION_FLAG_FIXTURE)
            putDouble(48, 0.0)
            putDouble(56, 200.0)
            putDouble(64, 0.25)
            putDouble(72, 0.0)
            putDouble(80, 0.5)
            putInt(88, 2)
            putInt(92, nameOffset)
            putInt(96, xOffset)
            putInt(100, yOffset)
            putInt(104, valuesOffset)
            putInt(108, editableOffset)
            position(nameOffset)
            put("VE".toByteArray())
            putDouble(xOffset, 1000.0)
            putDouble(xOffset + 8, 2000.0)
            putDouble(xOffset + 16, 3000.0)
            putDouble(yOffset, 40.0)
            putDouble(yOffset + 8, 100.0)
            doubleArrayOf(50.0, 55.0, 60.0, 70.0, 75.0, 80.0).forEachIndexed { index, value ->
                putDouble(valuesOffset + index * Double.SIZE_BYTES, value)
            }
            repeat(6) { index -> put(editableOffset + index, if (index == 4) 0 else 1) }
        }
    }
}
