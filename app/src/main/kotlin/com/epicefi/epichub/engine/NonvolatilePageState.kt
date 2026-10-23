package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class NonvolatilePageStatus {
    Unknown,
    PostBurnCrcVerified,
    StaleEvidence,
}

data class NonvolatilePageState(
    val status: NonvolatilePageStatus,
    val profileGeneration: Long,
    val draftRevision: Long,
    val sessionEpoch: Long,
    val page: Int,
    val expectedPageCrc32: Long?,
    val signatureCrc32: Long?,
)

internal const val NONVOLATILE_SNAPSHOT_SIZE = 48

internal fun decodeNonvolatilePageState(source: ByteBuffer, requestedPage: Int): NonvolatilePageState {
    require(source.capacity() >= NONVOLATILE_SNAPSHOT_SIZE) { "Nonvolatile state snapshot too small." }
    val buffer = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    buffer.position(0)
    val magic = ByteArray(4)
    buffer.get(magic)
    require(magic.contentEquals(byteArrayOf(78, 86, 80, 49))) { "Unexpected nonvolatile snapshot magic." }
    val status = when (buffer.get().toInt() and 0xFF) {
        0 -> NonvolatilePageStatus.Unknown
        1 -> NonvolatilePageStatus.PostBurnCrcVerified
        2 -> NonvolatilePageStatus.StaleEvidence
        else -> error("Unknown nonvolatile proof status.")
    }
    require((buffer.get().toInt() and 0xFF) == 1) { "Unsupported nonvolatile snapshot version." }
    require(buffer.short.toInt() == 0) { "Unexpected nonvolatile snapshot flags." }
    val generation = buffer.long
    val revision = buffer.long
    val epoch = buffer.long
    val page = buffer.int
    val crc = buffer.int.toLong() and 0xffff_ffffL
    val signatureCrc = buffer.int.toLong() and 0xffff_ffffL
    require(buffer.int == 0) { "Nonvolatile snapshot reserved bytes must be zero." }
    require(generation > 0 && revision >= 0 && epoch > 0 && page == requestedPage && page > 0) {
        "Nonvolatile state authority is inconsistent."
    }
    if (status == NonvolatilePageStatus.Unknown) {
        require(crc == 0L && signatureCrc == 0L) { "Unknown state cannot carry proof metadata." }
    }
    return NonvolatilePageState(
        status, generation, revision, epoch, page,
        if (status == NonvolatilePageStatus.Unknown) null else crc,
        if (status == NonvolatilePageStatus.Unknown) null else signatureCrc,
    )
}

internal class NativeNonvolatilePageStateBuffer {
    private val buffer = ByteBuffer.allocateDirect(NONVOLATILE_SNAPSHOT_SIZE).order(ByteOrder.LITTLE_ENDIAN)

    fun read(handle: Long, page: Int, signature: ByteArray): NonvolatilePageState {
        require(page > 0 && signature.isNotEmpty() && signature.size <= 4096)
        val signatureBuffer = ByteBuffer.allocateDirect(signature.size).apply {
            put(signature)
            rewind()
        }
        buffer.clear()
        val status = NativeEngineBridge.fillNonvolatilePageState(
            handle, page, signatureBuffer, signature.size, buffer,
        )
        check(status == 0) { "Native nonvolatile page state rejected request ($status)." }
        return decodeNonvolatilePageState(buffer, page)
    }
}
