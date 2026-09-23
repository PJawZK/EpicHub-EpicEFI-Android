package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object NativeDiagnosticBatchLayout {
    const val ABI_VERSION = 1
    const val HEADER_SIZE = 16
    const val EVENT_SIZE = 40
    const val MAX_EVENTS = 16
    const val BYTE_SIZE = HEADER_SIZE + (EVENT_SIZE * MAX_EVENTS)
}

enum class NativeDiagnosticCode(val code: Int) {
    ENGINE_CREATED(1),
    RUNTIME_SNAPSHOT_PUBLISHED(2),
    UNKNOWN(-1),
    ;

    companion object {
        fun fromCode(code: Int): NativeDiagnosticCode =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

enum class NativeDiagnosticSeverity(val code: Int) {
    TRACE(0),
    INFO(1),
    WARNING(2),
    ERROR(3),
    CRITICAL(4),
    UNKNOWN(-1),
    ;

    companion object {
        fun fromCode(code: Int): NativeDiagnosticSeverity =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

data class NativeDiagnosticEvent(
    val eventId: Long,
    val engineElapsedNanos: Long,
    val code: NativeDiagnosticCode,
    val rawCode: Int,
    val severity: NativeDiagnosticSeverity,
    val arg0: Long,
    val arg1: Long,
)

data class NativeDiagnosticBatch(
    val droppedTotal: Long,
    val events: List<NativeDiagnosticEvent>,
)

internal object NativeDiagnosticBatchCodec {
    fun decode(source: ByteBuffer): NativeDiagnosticBatch {
        require(source.capacity() >= NativeDiagnosticBatchLayout.BYTE_SIZE) {
            "Native diagnostic buffer is too small: ${source.capacity()} bytes."
        }

        val buffer = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val abiVersion = buffer.getInt(0)
        require(abiVersion == NativeDiagnosticBatchLayout.ABI_VERSION) {
            "Unsupported native diagnostic ABI $abiVersion; expected ${NativeDiagnosticBatchLayout.ABI_VERSION}."
        }

        val byteSize = buffer.getInt(4)
        require(byteSize == NativeDiagnosticBatchLayout.BYTE_SIZE) {
            "Unexpected native diagnostic batch size $byteSize; expected ${NativeDiagnosticBatchLayout.BYTE_SIZE}."
        }

        val eventCount = buffer.getInt(8)
        require(eventCount in 0..NativeDiagnosticBatchLayout.MAX_EVENTS) {
            "Invalid native diagnostic event count $eventCount."
        }

        val droppedTotal = buffer.getInt(12).toLong() and 0xFFFF_FFFFL
        val events = List(eventCount) { index ->
            val offset = NativeDiagnosticBatchLayout.HEADER_SIZE +
                (index * NativeDiagnosticBatchLayout.EVENT_SIZE)
            val rawCode = buffer.getInt(offset + 16)
            NativeDiagnosticEvent(
                eventId = buffer.getLong(offset),
                engineElapsedNanos = buffer.getLong(offset + 8),
                code = NativeDiagnosticCode.fromCode(rawCode),
                rawCode = rawCode,
                severity = NativeDiagnosticSeverity.fromCode(buffer.getInt(offset + 20)),
                arg0 = buffer.getLong(offset + 24),
                arg1 = buffer.getLong(offset + 32),
            )
        }

        return NativeDiagnosticBatch(
            droppedTotal = droppedTotal,
            events = events,
        )
    }
}

internal class NativeDiagnosticBatchBuffer {
    private val buffer: ByteBuffer = ByteBuffer
        .allocateDirect(NativeDiagnosticBatchLayout.BYTE_SIZE)
        .order(ByteOrder.LITTLE_ENDIAN)

    fun read(handle: Long): NativeDiagnosticBatch {
        buffer.clear()
        when (val status = NativeEngineBridge.fillDiagnosticEvents(handle, buffer)) {
            0 -> Unit
            1 -> error("Native diagnostic drain rejected an invalid engine handle.")
            2 -> error("Native diagnostic drain requires a direct ByteBuffer.")
            3 -> error("Native diagnostic buffer is smaller than the native ABI.")
            else -> error("Native diagnostic drain failed with status $status.")
        }
        return NativeDiagnosticBatchCodec.decode(buffer)
    }
}
