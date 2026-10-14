package com.epicefi.epichub.platform.transport.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbReadOnlyTransactionChannelTest {
    @Test
    fun returns_bounded_response_after_complete_request_transfer() {
        val port = FakePort(responseChunks = listOf(byteArrayOf(1, 2, 3)))
        val channel = UsbReadOnlyTransactionChannel(port)

        val result = channel.transact(byteArrayOf(0x53), maxResponseBytes = 8, timeoutMs = 250)

        val success = result as UsbReadOnlyTransactionResult.Success
        assertArrayEquals(byteArrayOf(1, 2, 3), success.response)
        assertArrayEquals(byteArrayOf(0x53), port.lastRequest)
        assertEquals(250, port.lastSendTimeoutMs)
        assertEquals(250, port.lastReceiveTimeoutMs)
    }

    @Test
    fun exact_transaction_collects_segmented_bulk_reads_until_declared_frame_length() {
        val port =
            FakePort(
                responseChunks =
                    listOf(
                        byteArrayOf(0, 4),
                        byteArrayOf(0, 9, 8),
                        byteArrayOf(7, 6, 5),
                    ),
            )
        val channel = UsbReadOnlyTransactionChannel(port)

        val result =
            channel.transactExact(
                request = byteArrayOf(1, 2, 3),
                expectedResponseBytes = 8,
                timeoutMs = 250,
            )

        val success = result as UsbReadOnlyTransactionResult.Success
        assertArrayEquals(byteArrayOf(0, 4, 0, 9, 8, 7, 6, 5), success.response)
        assertEquals(3, port.receiveCalls)
    }

    @Test
    fun exact_transaction_fails_closed_when_stream_ends_before_declared_frame_length() {
        val port =
            FakePort(
                responseChunks = listOf(byteArrayOf(1, 2), byteArrayOf(3)),
                forcedReceiveCounts = listOf(null, null, 0),
            )
        val channel = UsbReadOnlyTransactionChannel(port)

        val result =
            channel.transactExact(
                request = byteArrayOf(1),
                expectedResponseBytes = 4,
                timeoutMs = 100,
            )

        assertTrue(result is UsbReadOnlyTransactionResult.TransportFailure)
        assertEquals(3, port.receiveCalls)
    }

    @Test
    fun exact_transaction_rejects_invalid_expected_length_before_transport_io() {
        val port = FakePort(responseChunks = listOf(byteArrayOf(1)))
        val channel = UsbReadOnlyTransactionChannel(port)

        assertTrue(
            channel.transactExact(byteArrayOf(1), expectedResponseBytes = 0, timeoutMs = 100) is
                UsbReadOnlyTransactionResult.Rejected,
        )
        assertTrue(
            channel.transactExact(byteArrayOf(1), expectedResponseBytes = 65_537, timeoutMs = 100) is
                UsbReadOnlyTransactionResult.Rejected,
        )
        assertEquals(0, port.sendCalls)
        assertEquals(0, port.receiveCalls)
    }

    @Test
    fun rejects_invalid_bounds_before_transport_io() {
        val port = FakePort(responseChunks = listOf(byteArrayOf(1)))
        val channel = UsbReadOnlyTransactionChannel(port)

        assertTrue(
            channel.transact(byteArrayOf(), maxResponseBytes = 8, timeoutMs = 100) is
                UsbReadOnlyTransactionResult.Rejected,
        )
        assertTrue(
            channel.transact(byteArrayOf(1), maxResponseBytes = 0, timeoutMs = 100) is
                UsbReadOnlyTransactionResult.Rejected,
        )
        assertTrue(
            channel.transact(byteArrayOf(1), maxResponseBytes = 65_537, timeoutMs = 100) is
                UsbReadOnlyTransactionResult.Rejected,
        )
        assertTrue(
            channel.transact(byteArrayOf(1), maxResponseBytes = 8, timeoutMs = 0) is
                UsbReadOnlyTransactionResult.Rejected,
        )
        assertEquals(0, port.sendCalls)
        assertEquals(0, port.receiveCalls)
    }

    @Test
    fun fails_closed_on_partial_request_transfer() {
        val port =
            FakePort(
                responseChunks = listOf(byteArrayOf(1)),
                forcedSendCount = 1,
            )
        val channel = UsbReadOnlyTransactionChannel(port)

        val result = channel.transact(byteArrayOf(1, 2), maxResponseBytes = 8, timeoutMs = 100)

        assertTrue(result is UsbReadOnlyTransactionResult.TransportFailure)
        assertEquals(1, port.sendCalls)
        assertEquals(0, port.receiveCalls)
    }

    @Test
    fun rejects_response_that_exceeds_declared_bound() {
        val port = FakePort(responseChunks = listOf(byteArrayOf(1, 2, 3, 4, 5)))
        val channel = UsbReadOnlyTransactionChannel(port)

        val result = channel.transact(byteArrayOf(1), maxResponseBytes = 4, timeoutMs = 100)

        assertTrue(result is UsbReadOnlyTransactionResult.Rejected)
    }

    @Test
    fun fails_closed_on_empty_or_failed_response_transfer() {
        val zeroPort =
            FakePort(
                responseChunks = listOf(byteArrayOf()),
                forcedReceiveCounts = listOf(0),
            )
        val failedPort =
            FakePort(
                responseChunks = listOf(byteArrayOf()),
                forcedReceiveCounts = listOf(-1),
            )

        assertTrue(
            UsbReadOnlyTransactionChannel(zeroPort)
                .transact(byteArrayOf(1), maxResponseBytes = 8, timeoutMs = 100) is
                UsbReadOnlyTransactionResult.TransportFailure,
        )
        assertTrue(
            UsbReadOnlyTransactionChannel(failedPort)
                .transact(byteArrayOf(1), maxResponseBytes = 8, timeoutMs = 100) is
                UsbReadOnlyTransactionResult.TransportFailure,
        )
    }

    @Test
    fun close_is_forwarded_to_the_underlying_port() {
        val port = FakePort(responseChunks = listOf(byteArrayOf(1)))
        val channel = UsbReadOnlyTransactionChannel(port)

        assertFalse(port.closed)
        channel.close()
        assertTrue(port.closed)
    }

    private class FakePort(
        private val responseChunks: List<ByteArray>,
        private val forcedSendCount: Int? = null,
        private val forcedReceiveCounts: List<Int?> = emptyList(),
    ) : UsbReadOnlyBulkPort {
        var lastRequest: ByteArray? = null
        var lastSendTimeoutMs: Int? = null
        var lastReceiveTimeoutMs: Int? = null
        var sendCalls: Int = 0
        var receiveCalls: Int = 0
        var closed: Boolean = false

        override fun sendRequest(
            request: ByteArray,
            timeoutMs: Int,
        ): Int {
            sendCalls += 1
            lastRequest = request.copyOf()
            lastSendTimeoutMs = timeoutMs
            return forcedSendCount ?: request.size
        }

        override fun receiveResponse(
            buffer: ByteArray,
            timeoutMs: Int,
        ): Int {
            val callIndex = receiveCalls
            receiveCalls += 1
            lastReceiveTimeoutMs = timeoutMs
            val chunk = responseChunks.getOrElse(callIndex) { byteArrayOf() }
            val forced = forcedReceiveCounts.getOrNull(callIndex)
            val count = forced ?: chunk.size
            if (count > 0) {
                chunk.copyInto(buffer, endIndex = minOf(chunk.size, buffer.size, count))
            }
            return count
        }

        override fun close() {
            closed = true
        }
    }
}
