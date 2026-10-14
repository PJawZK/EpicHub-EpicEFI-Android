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
        if (request.isEmpty()) {
            return UsbReadOnlyTransactionResult.Rejected("request must not be empty")
        }
        if (maxResponseBytes <= 0 || maxResponseBytes > MAX_READ_ONLY_RESPONSE_BYTES) {
            return UsbReadOnlyTransactionResult.Rejected(
                "max response must be within 1..$MAX_READ_ONLY_RESPONSE_BYTES bytes",
            )
        }
        if (timeoutMs <= 0) {
            return UsbReadOnlyTransactionResult.Rejected("timeout must be positive")
        }

        val sent = port.sendRequest(request, timeoutMs)
        if (sent != request.size) {
            return UsbReadOnlyTransactionResult.TransportFailure(
                "request transfer incomplete: expected ${request.size}, sent $sent",
            )
        }

        val receiveBuffer = ByteArray(maxResponseBytes + 1)
        val received = port.receiveResponse(receiveBuffer, timeoutMs)
        if (received <= 0) {
            return UsbReadOnlyTransactionResult.TransportFailure(
                "response transfer returned $received bytes",
            )
        }
        if (received > maxResponseBytes) {
            return UsbReadOnlyTransactionResult.Rejected(
                "response exceeded declared bound of $maxResponseBytes bytes",
            )
        }

        return UsbReadOnlyTransactionResult.Success(receiveBuffer.copyOf(received))
    }

    override fun close() {
        port.close()
    }
}
