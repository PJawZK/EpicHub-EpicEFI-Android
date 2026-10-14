package com.epicefi.epichub.platform.transport.usb

import com.epicefi.epichub.engine.RuntimeIdentityResponseValidator
import com.epicefi.epichub.engine.RuntimeIdentityValidationError
import com.epicefi.epichub.engine.RuntimeIdentityValidationResult
import com.epicefi.epichub.engine.ValidatedRuntimeControllerIdentity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbRuntimeIdentityProbeTest {
    @Test
    fun forwards_untouched_transport_response_into_identity_validator() {
        val signature = "rusEFI target.dynamic.123"
        val port = FakePort(signature.toByteArray())
        val channel = UsbReadOnlyTransactionChannel(port)
        val validator = RecordingValidator()
        val probe = UsbRuntimeIdentityProbe(validator)
        val plan =
            UsbRuntimeIdentityProbePlan(
                request = byteArrayOf(0x51),
                maxResponseBytes = 128,
                timeoutMs = 300,
            )

        val result = probe.probe(channel, plan)

        val identity = result as UsbRuntimeIdentityProbeResult.Identity
        assertEquals(signature, identity.identity.signature)
        assertArrayEquals(signature.toByteArray(), validator.lastResponse)
        assertEquals(128, validator.lastMaxResponseBytes)
        assertArrayEquals(byteArrayOf(0x51), port.lastRequest)
    }

    @Test
    fun transport_failure_never_reaches_identity_validator() {
        val port = FakePort(response = byteArrayOf(), receiveCount = -1)
        val validator = RecordingValidator()
        val probe = UsbRuntimeIdentityProbe(validator)

        val result =
            probe.probe(
                UsbReadOnlyTransactionChannel(port),
                UsbRuntimeIdentityProbePlan(
                    request = byteArrayOf(1),
                    maxResponseBytes = 32,
                    timeoutMs = 100,
                ),
            )

        assertTrue(result is UsbRuntimeIdentityProbeResult.TransportRejected)
        assertEquals(0, validator.calls)
    }

    @Test
    fun native_identity_rejection_is_preserved() {
        val port = FakePort(byteArrayOf(0xff.toByte()))
        val validator =
            RuntimeIdentityResponseValidator { _, _ ->
                RuntimeIdentityValidationResult.Rejected(
                    error = RuntimeIdentityValidationError.INVALID_UTF8,
                    nativeStatus = 4,
                )
            }
        val probe = UsbRuntimeIdentityProbe(validator)

        val result =
            probe.probe(
                UsbReadOnlyTransactionChannel(port),
                UsbRuntimeIdentityProbePlan(
                    request = byteArrayOf(1),
                    maxResponseBytes = 32,
                    timeoutMs = 100,
                ),
            )

        val rejected = result as UsbRuntimeIdentityProbeResult.IdentityRejected
        assertEquals(RuntimeIdentityValidationError.INVALID_UTF8, rejected.result.error)
    }

    private class RecordingValidator : RuntimeIdentityResponseValidator {
        var calls: Int = 0
        var lastResponse: ByteArray? = null
        var lastMaxResponseBytes: Int? = null

        override fun validate(
            response: ByteArray,
            maxResponseBytes: Int,
        ): RuntimeIdentityValidationResult {
            calls += 1
            lastResponse = response.copyOf()
            lastMaxResponseBytes = maxResponseBytes
            return RuntimeIdentityValidationResult.Valid(
                ValidatedRuntimeControllerIdentity(response.toString(Charsets.UTF_8)),
            )
        }
    }

    private class FakePort(
        private val response: ByteArray,
        private val receiveCount: Int? = null,
    ) : UsbReadOnlyBulkPort {
        var lastRequest: ByteArray? = null

        override fun sendRequest(
            request: ByteArray,
            timeoutMs: Int,
        ): Int {
            lastRequest = request.copyOf()
            return request.size
        }

        override fun receiveResponse(
            buffer: ByteArray,
            timeoutMs: Int,
        ): Int {
            val count = receiveCount ?: response.size
            if (count > 0) {
                response.copyInto(buffer, endIndex = minOf(response.size, buffer.size))
            }
            return count
        }

        override fun close() = Unit
    }
}
