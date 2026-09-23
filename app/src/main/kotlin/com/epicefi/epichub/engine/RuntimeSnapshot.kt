package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object RuntimeSnapshotLayout {
    const val ABI_VERSION = 1
    const val BYTE_SIZE = 96
    const val HEADER_SIZE = 48
    const val CHANNEL_SIZE = 12
    const val MAX_CHANNELS = 4
}

enum class RuntimeChannelValidity(val code: Int) {
    INVALID(0),
    VALID(1),
    STALE(2),
    UNKNOWN(-1),
    ;

    companion object {
        fun fromCode(code: Int): RuntimeChannelValidity =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

data class RuntimeChannelSample(
    val id: Long,
    val validity: RuntimeChannelValidity,
    val value: Float,
)

data class RuntimeSnapshot(
    val sequence: Long,
    val monotonicNanos: Long,
    val sessionId: Long,
    val sourceKind: Int,
    val flags: Int,
    val channels: List<RuntimeChannelSample>,
)

internal object RuntimeSnapshotCodec {
    fun decode(source: ByteBuffer): RuntimeSnapshot {
        require(source.capacity() >= RuntimeSnapshotLayout.BYTE_SIZE) {
            "Runtime snapshot buffer is too small: ${source.capacity()} bytes."
        }

        val buffer = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val abiVersion = buffer.getInt(0)
        require(abiVersion == RuntimeSnapshotLayout.ABI_VERSION) {
            "Unsupported runtime snapshot ABI $abiVersion; expected ${RuntimeSnapshotLayout.ABI_VERSION}."
        }

        val byteSize = buffer.getInt(4)
        require(byteSize == RuntimeSnapshotLayout.BYTE_SIZE) {
            "Unexpected runtime snapshot size $byteSize; expected ${RuntimeSnapshotLayout.BYTE_SIZE}."
        }

        val channelCount = buffer.getInt(40)
        require(channelCount in 0..RuntimeSnapshotLayout.MAX_CHANNELS) {
            "Invalid runtime snapshot channel count $channelCount."
        }

        val channels = List(channelCount) { index ->
            val offset = RuntimeSnapshotLayout.HEADER_SIZE + (index * RuntimeSnapshotLayout.CHANNEL_SIZE)
            RuntimeChannelSample(
                id = buffer.getInt(offset).toLong() and 0xFFFF_FFFFL,
                validity = RuntimeChannelValidity.fromCode(buffer.getInt(offset + 4)),
                value = buffer.getFloat(offset + 8),
            )
        }

        return RuntimeSnapshot(
            sequence = buffer.getLong(8),
            monotonicNanos = buffer.getLong(16),
            sessionId = buffer.getLong(24),
            sourceKind = buffer.getInt(32),
            flags = buffer.getInt(36),
            channels = channels,
        )
    }
}

internal class RuntimeSnapshotBuffer {
    private val buffer: ByteBuffer = ByteBuffer
        .allocateDirect(RuntimeSnapshotLayout.BYTE_SIZE)
        .order(ByteOrder.LITTLE_ENDIAN)

    fun read(handle: Long): RuntimeSnapshot {
        buffer.clear()
        when (val status = NativeEngineBridge.fillRuntimeSnapshot(handle, buffer)) {
            0 -> Unit
            1 -> error("Native runtime snapshot rejected an invalid engine handle.")
            2 -> error("Native runtime snapshot requires a direct ByteBuffer.")
            3 -> error("Native runtime snapshot buffer is smaller than the native ABI.")
            else -> error("Native runtime snapshot failed with status $status.")
        }
        return RuntimeSnapshotCodec.decode(buffer)
    }
}
