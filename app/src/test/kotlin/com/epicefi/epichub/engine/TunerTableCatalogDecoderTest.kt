package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TunerTableCatalogDecoderTest {
    @Test
    fun decodesVersionedCatalogAndNames() {
        val name = "VE Table".encodeToByteArray()
        val totalSize = 40 + 24 + name.size
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(1)
        buffer.putInt(40)
        buffer.putInt(24)
        buffer.putInt(1)
        buffer.putLong(2_273_317_132L)
        buffer.putLong(3L)
        buffer.putInt(2)
        buffer.putInt(totalSize)
        buffer.putInt(1001)
        buffer.putInt(32)
        buffer.putInt(32)
        buffer.putInt(2)
        buffer.putInt(64)
        buffer.putInt(name.size)
        buffer.put(name)

        val catalog = TunerTableCatalogDecoder.decode(buffer)

        assertEquals(2_273_317_132L, catalog.profileGeneration)
        assertEquals(3L, catalog.draftRevision)
        assertEquals(TuneSourceOrigin.IMPORTED_FILE, catalog.origin)
        assertEquals(
            TunerTableCatalogEntry(
                tableId = 1001,
                name = "VE Table",
                rows = 32,
                columns = 32,
                displayDecimals = 2,
            ),
            catalog.tables.single(),
        )
    }

    @Test
    fun decodesLiveControllerOriginCode() {
        val buffer = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(1)
        buffer.putInt(40)
        buffer.putInt(24)
        buffer.putInt(0)
        buffer.putLong(4_242L)
        buffer.putLong(0L)
        buffer.putInt(3)
        buffer.putInt(40)

        val catalog = TunerTableCatalogDecoder.decode(buffer)

        assertEquals(4_242L, catalog.profileGeneration)
        assertEquals(TuneSourceOrigin.LIVE_CONTROLLER, catalog.origin)
        assertEquals(emptyList<TunerTableCatalogEntry>(), catalog.tables)
    }

    @Test
    fun rejectsNameRangeOutsideCatalog() {
        val buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(1)
        buffer.putInt(40)
        buffer.putInt(24)
        buffer.putInt(1)
        buffer.putLong(42L)
        buffer.putLong(0L)
        buffer.putInt(1)
        buffer.putInt(64)
        buffer.putInt(7)
        buffer.putInt(4)
        buffer.putInt(5)
        buffer.putInt(2)
        buffer.putInt(64)
        buffer.putInt(1)

        assertThrows(IllegalArgumentException::class.java) {
            TunerTableCatalogDecoder.decode(buffer)
        }
    }
}
