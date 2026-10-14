package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

enum class TuneSourceOrigin {
    OFFLINE_FIXTURE,
    IMPORTED_FILE,
    LIVE_CONTROLLER,
}

data class TunerTableCatalogEntry(
    val tableId: Int,
    val name: String,
    val rows: Int,
    val columns: Int,
    val displayDecimals: Int,
)

data class TunerTableCatalog(
    val profileGeneration: Long,
    val draftRevision: Long,
    val origin: TuneSourceOrigin,
    val tables: List<TunerTableCatalogEntry>,
)

internal object TunerTableCatalogDecoder {
    private const val ABI_VERSION = 1
    private const val HEADER_SIZE = 40
    private const val ENTRY_SIZE = 24

    fun decode(source: ByteBuffer): TunerTableCatalog {
        val buffer = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.capacity() >= HEADER_SIZE) { "Tune catalog buffer is truncated." }
        require(buffer.getInt(0) == ABI_VERSION) { "Unsupported tune catalog ABI." }
        require(buffer.getInt(4) == HEADER_SIZE) { "Unexpected tune catalog header size." }
        require(buffer.getInt(8) == ENTRY_SIZE) { "Unexpected tune catalog entry size." }

        val count = buffer.getInt(12)
        require(count >= 0) { "Negative tune catalog table count." }
        val profileGeneration = buffer.getLong(16)
        val draftRevision = buffer.getLong(24)
        val origin = when (buffer.getInt(32)) {
            1 -> TuneSourceOrigin.OFFLINE_FIXTURE
            2 -> TuneSourceOrigin.IMPORTED_FILE
            3 -> TuneSourceOrigin.LIVE_CONTROLLER
            else -> error("Unsupported tune source origin.")
        }
        val totalSize = buffer.getInt(36)
        require(totalSize == buffer.capacity()) { "Tune catalog size mismatch." }

        val entriesEnd = HEADER_SIZE.toLong() + count.toLong() * ENTRY_SIZE.toLong()
        require(entriesEnd <= totalSize.toLong()) { "Tune catalog entries exceed buffer." }

        val tables = ArrayList<TunerTableCatalogEntry>(count)
        val ids = HashSet<Int>(count)
        repeat(count) { index ->
            val offset = HEADER_SIZE + index * ENTRY_SIZE
            val tableId = buffer.getInt(offset)
            val rows = buffer.getInt(offset + 4)
            val columns = buffer.getInt(offset + 8)
            val displayDecimals = buffer.getInt(offset + 12)
            val nameOffset = buffer.getInt(offset + 16)
            val nameLength = buffer.getInt(offset + 20)

            require(tableId > 0) { "Invalid tune table ID." }
            require(ids.add(tableId)) { "Duplicate tune table ID $tableId." }
            require(rows > 0 && columns > 0) { "Invalid tune table dimensions." }
            require(displayDecimals >= 0) { "Invalid tune table display precision." }
            require(nameOffset >= entriesEnd && nameLength >= 0) { "Invalid tune table name range." }
            val nameEnd = nameOffset.toLong() + nameLength.toLong()
            require(nameEnd <= totalSize.toLong()) { "Tune table name exceeds catalog buffer." }

            val nameBytes = ByteArray(nameLength)
            val names = buffer.duplicate()
            names.position(nameOffset)
            names.get(nameBytes)
            val name = String(nameBytes, StandardCharsets.UTF_8)
            require(name.isNotBlank()) { "Tune table name is blank." }

            tables += TunerTableCatalogEntry(
                tableId = tableId,
                name = name,
                rows = rows,
                columns = columns,
                displayDecimals = displayDecimals,
            )
        }

        return TunerTableCatalog(
            profileGeneration = profileGeneration,
            draftRevision = draftRevision,
            origin = origin,
            tables = tables,
        )
    }
}

internal class TunerTableCatalogBuffer {
    fun read(handle: Long): TunerTableCatalog {
        val size = NativeEngineBridge.tuneCatalogSize(handle)
        check(size > 0) { "Native tune catalog size failed with status $size." }
        val buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN)
        val status = NativeEngineBridge.fillTuneCatalog(handle, buffer)
        check(status == 0) { "Native tune catalog read failed with status $status." }
        return TunerTableCatalogDecoder.decode(buffer)
    }
}
