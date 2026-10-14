package com.epicefi.epichub.platform.transport.usb

private const val MAX_READ_ONLY_RESPONSE_BYTES = 64 * 1024

internal interface UsbReadOnlyBulkPort : AutoCloseable {
    fun sendRequest(
        request: ByteArray,
        timeoutMs: Int,
    ): Int

    fun receiveResponse(
        buffer: ByteArray,
        timeoutMs: Int,
    ): Int
}

sealed interface UsbReadOnlyTransactionResult {
    data class Success(val response: ByteArray) : UsbReadOnlyTransactionResult

    data class Rejected(val reason: String) : UsbReadOnlyTransactionResult

    data class TransportFailure(val reason: String) : UsbReadOnlyTransactionResult
}

class UsbReadOnlyTransactionChannel internal constructor(
    private val port: UsbReadOnlyBulkPort,
) : AutoCloseable {
    fun transact(
        request: ByteArray,
        maxResponseBytes: Int,
        timeoutMs: Int,
    ): UsbReadOnlyTransactionResult {
        val validation = validateRequest(request, maxResponseBytes, timeoutMs)
        if (validation != null) {
            return validation
        }

        val sent = port.sendRequest(request, timeoutMs)
        if (sent != request.size) {
            return incompleteRequest(request.size, sent)
        }

        val receiveBuffer = ByteArray(maxResponseBytes + 1)
        val received = port.receiveResponse(receiveBuffer, timeoutMs)
        if (received <= 0) {
            return failedResponse(received)
        }
        if (received > maxResponseBytes) {
            return UsbReadOnlyTransactionResult.Rejected(
                "response exceeded declared bound of $maxResponseBytes bytes",
            )
        }

        return UsbReadOnlyTransactionResult.Success(receiveBuffer.copyOf(received))
    }

    fun transactExact(
        request: ByteArray,
        expectedResponseBytes: Int,
        timeoutMs: Int,
    ): UsbReadOnlyTransactionResult {
        val validation = validateRequest(request, expectedResponseBytes, timeoutMs)
        if (validation != null) {
            return validation
        }

        val sent = port.sendRequest(request, timeoutMs)
        if (sent != request.size) {
            return incompleteRequest(request.size, sent)
        }

        val response = ByteArray(expectedResponseBytes)
        var receivedTotal = 0
        while (receivedTotal < expectedResponseBytes) {
            val remaining = expectedResponseBytes - receivedTotal
            val chunk = ByteArray(remaining)
            val received = port.receiveResponse(chunk, timeoutMs)
            if (received <= 0) {
                return UsbReadOnlyTransactionResult.TransportFailure(
                    "response transfer ended after $receivedTotal of $expectedResponseBytes bytes; latest transfer returned $received bytes",
                )
            }
            if (received > remaining) {
                return UsbReadOnlyTransactionResult.Rejected(
                    "response transfer exceeded remaining bound: expected $remaining bytes, received $received",
                )
            }
            chunk.copyInto(response, destinationOffset = receivedTotal, endIndex = received)
            receivedTotal += received
        }

        return UsbReadOnlyTransactionResult.Success(response)
    }

    override fun close() {
        port.close()
    }

    private fun validateRequest(
        request: ByteArray,
        responseBound: Int,
        timeoutMs: Int,
    ): UsbReadOnlyTransactionResult.Rejected? {
        if (request.isEmpty()) {
            return UsbReadOnlyTransactionResult.Rejected("request must not be empty")
        }
        if (responseBound <= 0 || responseBound > MAX_READ_ONLY_RESPONSE_BYTES) {
            return UsbReadOnlyTransactionResult.Rejected(
                "response bound must be within 1..$MAX_READ_ONLY_RESPONSE_BYTES bytes",
            )
        }
        if (timeoutMs <= 0) {
            return UsbReadOnlyTransactionResult.Rejected("timeout must be positive")
        }
        return null
    }

    private fun incompleteRequest(
        expected: Int,
        sent: Int,
    ) = UsbReadOnlyTransactionResult.TransportFailure(
        "request transfer incomplete: expected $expected, sent $sent",
    )

    private fun failedResponse(received: Int) =
        UsbReadOnlyTransactionResult.TransportFailure(
            "response transfer returned $received bytes",
        )
}
