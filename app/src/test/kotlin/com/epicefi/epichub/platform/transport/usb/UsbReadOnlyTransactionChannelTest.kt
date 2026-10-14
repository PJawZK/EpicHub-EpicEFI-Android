package com.epicefi.epichub.platform.transport.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbReadOnlyTransactionChannelTest {
    @Test
    fun returns_bounded_response_after_complete_request_transfer() {
        val port = FakePort(response = byteArrayOf(1, 2, 3))
        val channel = UsbReadOnlyTransactionChannel(port)

        val result = channel.transact(byteArrayOf(0x53), maxResponseBytes = 8, timeoutMs = 250)

        val success = result as UsbReadOnlyTransactionResult.Success
        assertArrayEquals(byteArrayOf(1, 2, 3), success.response)
        assertArrayEquals(byteArrayOf(0x53), port.lastRequest)
        assertEquals(250, port.lastSendTimeoutMs)
        assertEquals(250, port.lastReceiveTimeoutMs)
    }

    @Test
    fun rejects_invalid_bounds_before_transport_io() {
        val port = FakePort(response = byteArrayOf(1))
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
        val port = FakePort(response = byteArrayOf(1), forcedSendCount = 1)
        val channel = UsbReadOnlyTransactionChannel(port)

        val result = channel.transact(byteArrayOf(1, 2), maxResponseBytes = 8, timeoutMs = 100)

        assertTrue(result is UsbReadOnlyTransactionResult.TransportFailure)
        assertEquals(1, port.sendCalls)
        assertEquals(0, port.receiveCalls)
    }

    @Test
    fun rejects_response_that_exceeds_declared_bound() {
        val port = FakePort(response = byteArrayOf(1, 2, 3, 4, 5))
        val channel = UsbReadOnlyTransactionChannel(port)

        val result = channel.transact(byteArrayOf(1), maxResponseBytes = 4, timeoutMs = 100)

        assertTrue(result is UsbReadOnlyTransactionResult.Rejected)
    }

    @Test
    fun fails_closed_on_empty_or_failed_response_transfer() {
        val zeroPort = FakePort(response = byteArrayOf(), forcedReceiveCount = 0)
        val failedPort = FakePort(response = byteArrayOf(), forcedReceiveCount = -1)

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
        val port = FakePort(response = byteArrayOf(1))
        val channel = UsbReadOnlyTransactionChannel(port)

        assertFalse(port.closed)
        channel.close()
        assertTrue(port.closed)
    }

    private class FakePort(
        private val response: ByteArray,
        private val forcedSendCount: Int? = null,
        private val forcedReceiveCount: Int? = null,
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
            receiveCalls += 1
            lastReceiveTimeoutMs = timeoutMs
            val count = forcedReceiveCount ?: response.size
            if (count > 0) {
                response.copyInto(buffer, endIndex = minOf(response.size, buffer.size))
            }
            return count
        }

        override fun close() {
            closed = true
        }
    }
}
