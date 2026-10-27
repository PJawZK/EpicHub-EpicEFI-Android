package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeChannelCatalogTest {
    private data class Entry(
        val id: Int,
        val name: String,
        val units: String,
        val kind: Int = 3,
        val first: Int = 0,
        val last: Int = 0,
    )

    private fun wire(
        epoch: Long = 44,
        generation: Long = 800,
        revision: Long = 27,
        entries: List<Entry> = listOf(
            Entry(0x1234, "rpm", "RPM", 3),
            Entry(0x4567, "clt", "°C", 4),
            Entry(0x7890, "limp", "", 9, 2, 4),
        ),
    ): ByteArray {
        val b = ByteBuffer.allocate(RuntimeCatalogWireV1.MAX_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        b.put("EPCT".toByteArray(Charsets.US_ASCII))
        b.putInt(1)
        b.putLong(epoch)
        b.putLong(generation)
        b.putLong(revision)
        b.putInt(entries.size)
        b.putInt(0)
        entries.forEach {
            val name = it.name.toByteArray(Charsets.UTF_8)
            val units = it.units.toByteArray(Charsets.UTF_8)
            b.putInt(it.id)
            b.put(it.kind.toByte())
            b.put(it.first.toByte())
            b.put(it.last.toByte())
            b.put(0.toByte())
            b.putShort(name.size.toShort())
            b.putShort(units.size.toShort())
            b.put(name)
            b.put(units)
        }
        val end = b.position()
        b.putInt(36, end)
        return b.array().copyOf(end)
    }

    private fun parse(bytes: ByteArray, epoch: Long = 44): LiveRuntimeChannelCatalog? {
        val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes)
        buffer.rewind()
        return RuntimeCatalogWireV1.decode(buffer, bytes.size, epoch)
    }

    @Test
    fun exactDefinitionMetadataSurvivesUtf8AndBitfieldTypeDecoding() {
        val decoded = parse(wire())!!
        assertEquals("real-ecu-v1", decoded.source)
        assertEquals(44L, decoded.epoch)
        assertEquals(800L, decoded.profileGeneration)
        assertEquals(27L, decoded.definitionRevision)
        assertEquals(listOf("rpm", "clt", "limp"), decoded.channels.map { it.name })
        assertEquals(listOf("RPM", "°C", ""), decoded.channels.map { it.units })
        assertEquals(listOf(3, 4, 9), decoded.channels.map { it.typeCode })
        assertEquals(2, decoded.channels[2].firstBit)
        assertEquals(4, decoded.channels[2].lastBit)
        assertEquals(0x1234L, decoded.channels[0].id)
    }

    @Test
    fun rejectEpochVersionAndTruncatedOrTrailingCatalog() {
        val original = wire()
        assertNull(parse(original, 45))
        assertNull(parse(original.copyOf(original.size - 1)))
        assertNull(parse(original + byteArrayOf(0)))
        val badVersion = original.clone().apply { this[4] = 2 }
        assertNull(parse(badVersion))
        val badCount = original.clone().apply { this[32] = 0 }
        assertNull(parse(badCount))
        val wrongLength = original.clone().apply { this[36] = 0 }
        assertNull(parse(wrongLength))
    }

    @Test
    fun rejectDuplicateIdsAndNamesAndUnsupportedTypes() {
        assertNull(parse(wire(entries = listOf(Entry(1, "rpm", "RPM"), Entry(1, "clt", "°C")))))
        assertNull(parse(wire(entries = listOf(Entry(1, "rpm", "RPM"), Entry(2, "rpm", "°C")))))
        assertNull(parse(wire(entries = listOf(Entry(1, "rpm", "RPM", kind = 11)))))
        assertNull(parse(wire(entries = listOf(Entry(1, "rpm", "RPM", kind = 9, first = 9, last = 3)))))
        assertNull(parse(wire(entries = listOf(Entry(1, "rpm", "RPM", kind = 1, first = 1)))))
    }

    @Test
    fun rejectInvalidUtf8MetadataUntrustedNameAndExcessiveSizes() {
        val original = wire(entries = listOf(Entry(1, "rpm", "RPM")))
        val broken = original.clone().apply { this[52] = 0xff.toByte() }
        assertNull(parse(broken))
        assertNull(parse(wire(entries = listOf(Entry(1, "bad name", "RPM")))))
        assertNull(parse(wire(entries = listOf(Entry(1, "a".repeat(97), "RPM")))))
        assertNull(parse(wire(entries = listOf(Entry(1, "rpm", "u".repeat(65))))))
        assertNull(parse(wire(entries = listOf(Entry(1, "rpm", "bad\u0000unit")))))
    }

    @Test
    fun mockJniReaderMustMatchSelectedEpochAndValidRegistry() {
        val payload = wire()
        var calls = 0
        val ops = NativeRuntimeCatalogOps { handle, epoch, output ->
            calls++
            assertEquals(123L, handle)
            assertEquals(44L, epoch)
            assertTrue(output.isDirect)
            assertEquals(RuntimeCatalogWireV1.MAX_BYTES, output.capacity())
            output.put(payload)
            payload.size
        }
        val reader = NativeRuntimeCatalogReader({ 123L }, { true }, ops)
        val result = reader.read(44)
        assertTrue(result is RuntimeCatalogResult.Available)
        assertEquals(3, (result as RuntimeCatalogResult.Available).catalog.channels.size)
        assertEquals(1, calls)

        assertTrue(NativeRuntimeCatalogReader({ 0L }, { true }, ops).read(44)
            is RuntimeCatalogResult.Unavailable)
        assertTrue(NativeRuntimeCatalogReader({ 123L }, { false }, ops).read(44)
            is RuntimeCatalogResult.Unavailable)
        assertEquals(1, calls)
    }

    @Test
    fun mockNativeCatalogFailureAndStaleEpochRemainUnavailable() {
        val negative = NativeRuntimeCatalogReader(
            { 8L }, { true }, NativeRuntimeCatalogOps { _, _, _ -> -3 },
        )
        assertTrue(negative.read(44) is RuntimeCatalogResult.Unavailable)
        val payload = wire(epoch = 45L)
        val forged = NativeRuntimeCatalogReader(
            { 8L }, { true }, NativeRuntimeCatalogOps { _, _, b ->
                b.put(payload)
                payload.size
            },
        )
        assertFalse(forged.read(44) is RuntimeCatalogResult.Available)
    }
}
