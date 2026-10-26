package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyBulkPort
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeReadCycleTest {
    private val request = byteArrayOf(0, 5, 'O'.code.toByte(), 0, 0, 4, 0, 0, 0, 0, 0)

    @Test
    fun oneFragmentedUsbFramePublishesOnlyVerifiedNativeFreshBatch() {
        val ops = FakeRuntimeOps()
        val port = FakePort(frame(915.5f), maxChunk = 3)
        val result = cycle(ops).read(9, UsbReadOnlyTransactionChannel(port), 50)
        assertTrue(result is RuntimeReadCycleResult.Fresh)
        val frame = (result as RuntimeReadCycleResult.Fresh).frame
        assertEquals(9L, frame.epoch)
        assertEquals(1L, frame.sequence)
        assertEquals(800L, frame.profileGeneration)
        assertEquals(101L, frame.readings.single().id)
        assertEquals(915.5f, frame.readings.single().value, 0.001f)
        assertEquals(1, port.sends)
        assertEquals(1, ops.completions)
        assertEquals(request.toList(), port.lastRequest?.toList())
    }

    @Test
    fun crcErrorRejectsAllSamplesAndNoSecondRequestIsIssued() {
        val corrupt = frame(915.5f).apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
        val ops = FakeRuntimeOps()
        val port = FakePort(corrupt)
        val result = cycle(ops).read(9, UsbReadOnlyTransactionChannel(port), 50)
        assertTrue(result is RuntimeReadCycleResult.Unavailable)
        assertEquals(1, ops.completions)
        assertEquals(1, port.sends)
    }

    @Test
    fun shortTransferAndTimeoutNeverCompleteNativeRequest() {
        val ops = FakeRuntimeOps()
        val incomplete = FakePort(frame(400f).copyOfRange(0, 4))
        val partial = cycle(ops).read(9, UsbReadOnlyTransactionChannel(incomplete), 50)
        assertTrue(partial is RuntimeReadCycleResult.Unavailable)
        assertEquals(0, ops.completions)
        var clock = 0L
        val expired = cycle(ops) { val time = clock; clock += 100; time }
            .read(9, UsbReadOnlyTransactionChannel(FakePort(frame(400f))), 50)
        assertTrue(expired is RuntimeReadCycleResult.Unavailable)
        assertEquals(0, ops.completions)
    }

    @Test
    fun staleNativeAuthorityOrWrongEpochNeverStartsUsbIoOrYieldsFresh() {
        val ops = FakeRuntimeOps(prepareStatus = -3)
        val port = FakePort(frame(999f))
        assertTrue(cycle(ops).read(9, UsbReadOnlyTransactionChannel(port), 50) is RuntimeReadCycleResult.Unavailable)
        assertEquals(0, port.sends)

        val forged = FakeRuntimeOps(wrongEpoch = true)
        assertTrue(cycle(forged).read(9, UsbReadOnlyTransactionChannel(FakePort(frame(999f))), 50)
            is RuntimeReadCycleResult.Unavailable)
        assertEquals(1, forged.completions)
    }

    @Test
    fun decoderRejectsMalformedVersionAndChannelValidity() {
        val payload = ByteBuffer.allocateDirect(68).order(ByteOrder.LITTLE_ENDIAN)
        writeBatch(payload, epoch = 9L, version = 2)
        assertEquals(null, RuntimeReadWireV1.decode(payload, 68, 9L, 1L))
        writeBatch(payload, epoch = 9L, version = 1, sampleState = 3)
        assertEquals(null, RuntimeReadWireV1.decode(payload, 68, 9L, 1L))
        writeBatch(payload, epoch = 9L, version = 1, sampleState = 1)
        assertTrue(RuntimeReadWireV1.decode(payload, 68, 9L, 1L) is RuntimeReadCycleResult.Fresh)
    }

    private fun cycle(
        ops: FakeRuntimeOps,
        monotonic: () -> Long = { 10L },
    ) = NativeRuntimeReadCycle(handle = { 7L }, alive = { true }, ops = ops, monotonicMs = monotonic)

    private fun frame(value: Float): ByteArray {
        val body = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
            .put(0.toByte()).putFloat(value).array()
        val crc = CRC32().apply { update(body) }.value
        return ByteBuffer.allocate(body.size + 6).order(ByteOrder.BIG_ENDIAN)
            .putShort(body.size.toShort()).put(body).putInt(crc.toInt()).array()
    }

    private fun writeBatch(
        buffer: ByteBuffer,
        epoch: Long,
        version: Int = 1,
        sampleState: Int = 1,
    ) {
        buffer.clear()
        buffer.put(byteArrayOf('E'.code.toByte(), 'P'.code.toByte(), 'I'.code.toByte(), 'C'.code.toByte()))
        buffer.putInt(version)
        buffer.putLong(epoch)
        buffer.putLong(1L)
        buffer.putLong(10L)
        buffer.putLong(800L)
        buffer.putLong(42L)
        buffer.putInt(1)
        buffer.putInt(1)
        buffer.putInt(101)
        buffer.putFloat(915.5f)
        buffer.putInt(sampleState)
        buffer.rewind()
    }

    private inner class FakeRuntimeOps(
        private val prepareStatus: Long = 1L,
        private val wrongEpoch: Boolean = false,
    ) : NativeRuntimeReadOps {
        var completions = 0
        override fun prepare(
            handle: Long, epoch: Long, nowMs: Long, timeoutMs: Long, output: ByteBuffer,
        ): Long {
            if (prepareStatus < 0L) return prepareStatus
            output.order(ByteOrder.LITTLE_ENDIAN)
            output.putInt(11)
            output.putInt(request.size)
            output.put(request)
            return prepareStatus
        }
        override fun finish(
            handle: Long, epoch: Long, sequence: Long, nowMs: Long,
            response: ByteBuffer, responseLength: Int, output: ByteBuffer,
        ): Int {
            completions++
            val bytes = ByteArray(responseLength)
            response.get(bytes)
            if (bytes.size != 11) return -4
            val body = bytes.copyOfRange(2, 7)
            val crc = CRC32().apply { update(body) }.value.toInt()
            if (ByteBuffer.wrap(bytes, 7, 4).order(ByteOrder.BIG_ENDIAN).int != crc ||
                body[0] != 0.toByte()
            ) return -4
            writeBatch(output, epoch = if (wrongEpoch) epoch + 1 else epoch)
            return 68
        }
    }

    private class FakePort(
        private val bytes: ByteArray,
        private val maxChunk: Int = 100,
    ) : UsbReadOnlyBulkPort {
        var sends = 0
        var lastRequest: ByteArray? = null
        private var cursor = 0
        override fun sendRequest(request: ByteArray, timeoutMs: Int): Int {
            sends++
            lastRequest = request.copyOf()
            return request.size
        }
        override fun receiveResponse(buffer: ByteArray, timeoutMs: Int): Int {
            if (cursor == bytes.size) return -1
            val copied = minOf(bytes.size - cursor, buffer.size, maxChunk)
            bytes.copyInto(buffer, endIndex = cursor + copied, startIndex = cursor)
            cursor += copied
            return copied
        }
        override fun close() = Unit
    }
}
