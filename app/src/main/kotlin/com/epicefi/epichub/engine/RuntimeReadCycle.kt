package com.epicefi.epichub.engine

import android.os.SystemClock
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionResult
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Not the legacy synthetic RuntimeSnapshot; this source is explicitly ECU/CRC verified. */
internal data class LiveRuntimeReading(val id: Long, val value: Float)
internal data class LiveRuntimeFrame(
    val epoch: Long,
    val sequence: Long,
    val receivedAtMs: Long,
    val profileGeneration: Long,
    val definitionRevision: Long,
    val readings: List<LiveRuntimeReading>,
)
internal sealed interface RuntimeReadCycleResult {
    data class Fresh(val frame: LiveRuntimeFrame) : RuntimeReadCycleResult
    data class Unavailable(val reason: String) : RuntimeReadCycleResult
}

internal interface NativeRuntimeReadOps {
    fun prepare(
        handle: Long, epoch: Long, nowMs: Long, timeoutMs: Long, output: ByteBuffer,
    ): Long

    fun finish(
        handle: Long, epoch: Long, sequence: Long, nowMs: Long,
        response: ByteBuffer, responseLength: Int, output: ByteBuffer,
    ): Int
}

internal object JniNativeRuntimeReadOps : NativeRuntimeReadOps {
    override fun prepare(
        handle: Long, epoch: Long, nowMs: Long, timeoutMs: Long, output: ByteBuffer,
    ): Long = NativeProfileRegistryBridge.prepareRuntimeRead(handle, epoch, nowMs, timeoutMs, output)

    override fun finish(
        handle: Long, epoch: Long, sequence: Long, nowMs: Long,
        response: ByteBuffer, responseLength: Int, output: ByteBuffer,
    ): Int = NativeProfileRegistryBridge.finishRuntimeRead(
        handle, epoch, sequence, nowMs, response, responseLength, output,
    )
}

/**
 * One complete read, never a poller. Only the retained USB owner may call this while
 * holding EngineAuthoritySerialization from prepare through physical IO and finish.
 * JNI owns the bound profile/definition and verifies CRC, status, token, epoch and decode.
 */
internal class NativeRuntimeReadCycle(
    private val handle: () -> Long,
    private val alive: () -> Boolean,
    private val ops: NativeRuntimeReadOps = JniNativeRuntimeReadOps,
    private val monotonicMs: () -> Long = { SystemClock.elapsedRealtime() },
) {
    fun read(
        epoch: Long,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): RuntimeReadCycleResult {
        val registry = handle()
        if (registry <= 0L || !alive() || epoch <= 0L || timeoutMs <= 0) {
            return RuntimeReadCycleResult.Unavailable("Runtime registry or request is unavailable.")
        }
        val request = ByteBuffer.allocateDirect(72).order(ByteOrder.LITTLE_ENDIAN)
        val start = monotonicMs()
        if (start < 0) return RuntimeReadCycleResult.Unavailable("Monotonic clock unavailable.")
        val sequence = ops.prepare(registry, epoch, start, timeoutMs.toLong(), request)
        if (sequence <= 0) return RuntimeReadCycleResult.Unavailable("Native read preparation rejected: $sequence")
        request.position(0)
        val responseBytes = request.int
        val requestBytes = request.int
        if (requestBytes !in 1..64 || responseBytes !in 1..65_536 || requestBytes > request.capacity() - 8) {
            return RuntimeReadCycleResult.Unavailable("Native read plan exceeds transport limits.")
        }
        val requestPayload = ByteArray(requestBytes)
        request.get(requestPayload)
        val response = when (val result = channel.transactExact(requestPayload, responseBytes, timeoutMs)) {
            is UsbReadOnlyTransactionResult.Success -> result.response
            is UsbReadOnlyTransactionResult.Rejected ->
                return RuntimeReadCycleResult.Unavailable("USB runtime request rejected: ${result.reason}")
            is UsbReadOnlyTransactionResult.TransportFailure ->
                return RuntimeReadCycleResult.Unavailable("USB runtime transfer failed: ${result.reason}")
        }
        if (response.size != responseBytes) {
            return RuntimeReadCycleResult.Unavailable("Incomplete runtime response.")
        }
        val completed = monotonicMs()
        if (completed < start || completed - start >= timeoutMs.toLong()) {
            return RuntimeReadCycleResult.Unavailable("Runtime request expired before completion.")
        }
        val input = ByteBuffer.allocateDirect(response.size).apply {
            put(response)
            rewind()
        }
        val output = ByteBuffer.allocateDirect(MAX_BATCH_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val size = ops.finish(registry, epoch, sequence, completed, input, response.size, output)
        if (size <= 0) {
            return RuntimeReadCycleResult.Unavailable("Native CRC/identity/runtime verification rejected: $size")
        }
        return RuntimeReadWireV1.decode(output, size, epoch, sequence)
            ?: RuntimeReadCycleResult.Unavailable("Invalid versioned real-channel batch.")
    }

    private companion object {
        const val MAX_BATCH_BYTES = 56 + 1_024 * 12
    }
}

/** Strict, bounded read-only wire interpretation; no Dashboard consumers yet. */
internal object RuntimeReadWireV1 {
    fun decode(
        buffer: ByteBuffer,
        bytes: Int,
        expectedEpoch: Long,
        expectedSequence: Long,
    ): RuntimeReadCycleResult.Fresh? {
        if (bytes < 68 || bytes > buffer.capacity() || expectedEpoch <= 0 || expectedSequence <= 0) return null
        val source = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).apply { position(0); limit(bytes) }
        if (source.int != 0x43495045 || source.int != 1) return null // 'EPIC', version 1
        val epoch = source.long
        val sequence = source.long
        val timestamp = source.long
        val generation = source.long
        val revision = source.long
        val count = source.int
        val validity = source.int
        if (epoch != expectedEpoch || sequence != expectedSequence || timestamp < 0 ||
            generation <= 0 || count !in 1..1_024 || validity != 1 || bytes != 56 + count * 12
        ) return null
        val ids = HashSet<Long>(count)
        val readings = ArrayList<LiveRuntimeReading>(count)
        repeat(count) {
            val id = source.int.toLong() and 0xffff_ffffL
            val value = source.float
            val state = source.int
            if (id == 0L || !ids.add(id) || !value.isFinite() || state != 1) return null
            readings += LiveRuntimeReading(id, value)
        }
        return RuntimeReadCycleResult.Fresh(
            LiveRuntimeFrame(epoch, sequence, timestamp, generation, revision, readings),
        )
    }
}
