package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction

/** Definition-derived metadata only; must not be confused with measured ECU samples. */
internal data class LiveRuntimeChannelDefinition(
    val id: Long,
    val name: String,
    val units: String,
    val typeCode: Int,
    val firstBit: Int,
    val lastBit: Int,
)
internal data class LiveRuntimeChannelCatalog(
    val source: String = "real-ecu-v1",
    val epoch: Long,
    val profileGeneration: Long,
    val definitionRevision: Long,
    val channels: List<LiveRuntimeChannelDefinition>,
)

internal sealed interface RuntimeCatalogResult {
    data class Available(val catalog: LiveRuntimeChannelCatalog) : RuntimeCatalogResult
    data class Unavailable(val reason: String) : RuntimeCatalogResult
}

internal fun interface NativeRuntimeCatalogOps {
    fun fetch(handle: Long, epoch: Long, output: ByteBuffer): Int
}

internal object JniRuntimeCatalogOps : NativeRuntimeCatalogOps {
    override fun fetch(handle: Long, epoch: Long, output: ByteBuffer): Int =
        NativeProfileRegistryBridge.runtimeChannelCatalog(handle, epoch, output)
}

internal class NativeRuntimeCatalogReader(
    private val handle: () -> Long,
    private val alive: () -> Boolean,
    private val ops: NativeRuntimeCatalogOps = JniRuntimeCatalogOps,
) {
    fun read(epoch: Long): RuntimeCatalogResult {
        if (epoch <= 0L || !alive()) return RuntimeCatalogResult.Unavailable("No current native runtime lease.")
        val registry = handle()
        if (registry <= 0L) return RuntimeCatalogResult.Unavailable("Native runtime registry closed.")
        val output = ByteBuffer.allocateDirect(RuntimeCatalogWireV1.MAX_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val length = ops.fetch(registry, epoch, output)
        if (length <= 0) return RuntimeCatalogResult.Unavailable("Native runtime metadata rejected: $length")
        val result = RuntimeCatalogWireV1.decode(output, length, epoch)
            ?: return RuntimeCatalogResult.Unavailable("Malformed or mismatched native runtime catalog.")
        return RuntimeCatalogResult.Available(result)
    }
}

/**
 * EPCT v1, little endian:
 * [magic u32, version u32, epoch u64, generation u64, revision u64, count u32, bytes u32]
 * entries [id u32, type u8, firstBit u8, lastBit u8, reserved u8, nameLen u16,
 *          unitsLen u16, exact definition UTF-8 name and units].
 */
internal object RuntimeCatalogWireV1 {
    const val MAX_BYTES = 131_072
    private const val HEADER_BYTES = 40
    private const val ENTRY_BYTES = 12

    fun decode(source: ByteBuffer, size: Int, expectedEpoch: Long): LiveRuntimeChannelCatalog? {
        if (expectedEpoch <= 0L || size !in (HEADER_BYTES + ENTRY_BYTES + 1)..MAX_BYTES ||
            size > source.capacity()
        ) return null
        val data = source.duplicate().order(ByteOrder.LITTLE_ENDIAN).apply {
            position(0)
            limit(size)
        }
        if (data.int != 0x54435045 || data.int != 1) return null // 'EPCT', v1
        val epoch = data.long
        val generation = data.long
        val revision = data.long
        val count = data.int
        val declaredSize = data.int
        if (epoch != expectedEpoch || generation <= 0L || count !in 1..1_024 ||
            declaredSize != size || data.remaining() < count * (ENTRY_BYTES + 1)
        ) return null
        val ids = HashSet<Long>(count)
        val names = HashSet<String>(count)
        val channels = ArrayList<LiveRuntimeChannelDefinition>(count)
        repeat(count) {
            if (data.remaining() < ENTRY_BYTES) return null
            val id = data.int.toLong() and 0xffff_ffffL
            val type = data.get().toInt() and 0xff
            val first = data.get().toInt() and 0xff
            val last = data.get().toInt() and 0xff
            val reserved = data.get().toInt() and 0xff
            val nameLength = data.short.toInt() and 0xffff
            val unitsLength = data.short.toInt() and 0xffff
            if (id == 0L || !ids.add(id) || type !in 1..10 || reserved != 0 ||
                (type <= 7 && (first != 0 || last != 0)) ||
                (type >= 8 && (first > last || last >= when (type) {
                    8 -> 8
                    9 -> 16
                    else -> 32
                })) ||
                nameLength !in 1..96 || unitsLength > 64 ||
                data.remaining() < nameLength + unitsLength
            ) return null
            val name = decodeUtf8(data, nameLength) ?: return null
            val units = decodeUtf8(data, unitsLength) ?: return null
            if (!name.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' } ||
                !names.add(name) || units.any { Character.isISOControl(it) }
            ) return null
            channels += LiveRuntimeChannelDefinition(id, name, units, type, first, last)
        }
        if (data.hasRemaining()) return null
        return LiveRuntimeChannelCatalog(epoch = epoch, profileGeneration = generation,
            definitionRevision = revision, channels = channels)
    }

    private fun decodeUtf8(data: ByteBuffer, length: Int): String? {
        val bytes = ByteArray(length)
        data.get(bytes)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            null
        }
    }
}
