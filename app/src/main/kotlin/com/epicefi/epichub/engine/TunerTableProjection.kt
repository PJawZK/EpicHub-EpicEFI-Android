package com.epicefi.epichub.engine

import java.lang.Math.multiplyExact
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TABLE_PROJECTION_ABI_VERSION = 1
private const val TABLE_PROJECTION_HEADER_SIZE = 112
const val TABLE_PROJECTION_FLAG_OFFLINE = 1 shl 0
const val TABLE_PROJECTION_FLAG_FIXTURE = 1 shl 1

internal const val NATIVE_BUFFER_STATUS_OK = 0

data class TunerTableProjection(
    val profileGeneration: Long,
    val draftRevision: Long,
    val originCode: Int,
    val tableId: Int,
    val name: String,
    val rows: Int,
    val cols: Int,
    val displayDecimals: Int,
    val flags: Int,
    val minimum: Double,
    val maximum: Double,
    val quantum: Double,
    val quantumOrigin: Double,
    val editIncrement: Double,
    val xAxis: DoubleArray,
    val yAxis: DoubleArray,
    val values: DoubleArray,
    val editable: BooleanArray,
) {
    val isOffline: Boolean get() = flags and TABLE_PROJECTION_FLAG_OFFLINE != 0
    val isFixture: Boolean get() = flags and TABLE_PROJECTION_FLAG_FIXTURE != 0
}

internal object TunerTableProjectionDecoder {
    fun decode(source: ByteBuffer): TunerTableProjection {
        val buffer = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.capacity() >= TABLE_PROJECTION_HEADER_SIZE) { "Table projection header truncated." }

        val abiVersion = buffer.getInt(0)
        require(abiVersion == TABLE_PROJECTION_ABI_VERSION) {
            "Unsupported table projection ABI $abiVersion."
        }
        val totalSize = buffer.getInt(4)
        require(totalSize in TABLE_PROJECTION_HEADER_SIZE..buffer.capacity()) {
            "Invalid table projection size $totalSize."
        }

        val profileGeneration = buffer.getLong(8)
        val draftRevision = buffer.getLong(16)
        val originCode = buffer.getInt(24)
        val tableId = buffer.getInt(28)
        val rows = buffer.getInt(32)
        val cols = buffer.getInt(36)
        val displayDecimals = buffer.getInt(40)
        val flags = buffer.getInt(44)
        val minimum = buffer.getDouble(48)
        val maximum = buffer.getDouble(56)
        val quantum = buffer.getDouble(64)
        val quantumOrigin = buffer.getDouble(72)
        val editIncrement = buffer.getDouble(80)
        val nameLength = buffer.getInt(88)
        val nameOffset = buffer.getInt(92)
        val xOffset = buffer.getInt(96)
        val yOffset = buffer.getInt(100)
        val valuesOffset = buffer.getInt(104)
        val editableOffset = buffer.getInt(108)

        require(rows > 0 && cols > 0) { "Invalid table dimensions $rows x $cols." }
        require(displayDecimals in 0..9) { "Invalid display precision $displayDecimals." }
        require(nameLength >= 0) { "Negative table name length." }

        val cellCount = multiplyExact(rows, cols)
        requireRange(nameOffset, nameLength, totalSize, "name")
        requireRange(xOffset, multiplyExact(cols, Double.SIZE_BYTES), totalSize, "x axis")
        requireRange(yOffset, multiplyExact(rows, Double.SIZE_BYTES), totalSize, "y axis")
        requireRange(valuesOffset, multiplyExact(cellCount, Double.SIZE_BYTES), totalSize, "values")
        requireRange(editableOffset, cellCount, totalSize, "editability")

        val nameBytes = ByteArray(nameLength)
        val nameView = buffer.duplicate()
        nameView.position(nameOffset)
        nameView.get(nameBytes)

        return TunerTableProjection(
            profileGeneration = profileGeneration,
            draftRevision = draftRevision,
            originCode = originCode,
            tableId = tableId,
            name = nameBytes.toString(Charsets.UTF_8),
            rows = rows,
            cols = cols,
            displayDecimals = displayDecimals,
            flags = flags,
            minimum = minimum,
            maximum = maximum,
            quantum = quantum,
            quantumOrigin = quantumOrigin,
            editIncrement = editIncrement,
            xAxis = readDoubleArray(buffer, xOffset, cols),
            yAxis = readDoubleArray(buffer, yOffset, rows),
            values = readDoubleArray(buffer, valuesOffset, cellCount),
            editable = BooleanArray(cellCount) { index -> buffer.get(editableOffset + index).toInt() != 0 },
        )
    }

    private fun requireRange(offset: Int, length: Int, totalSize: Int, label: String) {
        require(offset >= TABLE_PROJECTION_HEADER_SIZE && length >= 0 && offset <= totalSize - length) {
            "Invalid $label range offset=$offset length=$length size=$totalSize."
        }
    }

    private fun readDoubleArray(buffer: ByteBuffer, offset: Int, count: Int): DoubleArray =
        DoubleArray(count) { index -> buffer.getDouble(offset + index * Double.SIZE_BYTES) }
}
