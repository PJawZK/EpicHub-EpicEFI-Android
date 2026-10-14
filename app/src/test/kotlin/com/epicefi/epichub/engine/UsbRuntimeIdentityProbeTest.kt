package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyBulkPort
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbRuntimeIdentityProbeTest {
    @Test
    fun exact_signature_transaction_flows_into_native_authority_without_normalization() {
        val signature = "rusEFI master.2026.08.26.MEGA144H7.2273317132"
        val port = FakePort(response = signature.toByteArray())
        val channel = UsbReadOnlyTransactionChannel(port)
        var authorityCalled = false
        val probe =
            UsbRuntimeIdentityProbe(
                RuntimeIdentityResponseAuthority { response, maxResponseBytes ->
                    authorityCalled = true
                    assertArrayEquals(signature.toByteArray(), response)
                    assertEquals(256, maxResponseBytes)
                    RuntimeIdentityValidationResult.Accepted(
                        ValidatedRuntimeIdentity(signature),
                    )
                },
            )

        val result =
            probe.probe(
                channel,
                RuntimeIdentityProbePlan(
                    query = byteArrayOf('S'.code.toByte()),
                    maxResponseBytes = 256,
                    timeoutMs = 1_000,
                ),
            )

        assertEquals(
            UsbRuntimeIdentityProbeResult.Identified(ValidatedRuntimeIdentity(signature)),
            result,
        )
        assertArrayEquals(byteArrayOf('S'.code.toByte()), port.lastRequest)
        assertEquals(1_000, port.lastTimeoutMs)
        assertTrue(authorityCalled)
    }

    @Test
    fun transport_failure_never_reaches_identity_authority() {
        val port = FakePort(response = byteArrayOf(), sendResult = 0)
        val channel = UsbReadOnlyTransactionChannel(port)
        var authorityCalled = false
        val probe =
            UsbRuntimeIdentityProbe(
                RuntimeIdentityResponseAuthority { _, _ ->
                    authorityCalled = true
                    RuntimeIdentityValidationResult.Accepted(ValidatedRuntimeIdentity("unexpected"))
                },
            )

        val result =
            probe.probe(
                channel,
                RuntimeIdentityProbePlan(
                    query = byteArrayOf('S'.code.toByte()),
                    maxResponseBytes = 256,
                    timeoutMs = 1_000,
                ),
            )

        assertTrue(result is UsbRuntimeIdentityProbeResult.TransportFailure)
        assertFalse(authorityCalled)
    }

    @Test
    fun native_identity_rejection_stays_explicit_and_does_not_become_transport_success() {
        val port = FakePort(response = byteArrayOf(0xff.toByte()))
        val channel = UsbReadOnlyTransactionChannel(port)
        val probe =
            UsbRuntimeIdentityProbe(
                RuntimeIdentityResponseAuthority { _, _ ->
                    RuntimeIdentityValidationResult.Rejected(RuntimeIdentityRejection.InvalidUtf8)
                },
            )

        assertEquals(
            UsbRuntimeIdentityProbeResult.IdentityRejected(RuntimeIdentityRejection.InvalidUtf8),
            probe.probe(
                channel,
                RuntimeIdentityProbePlan(
                    query = byteArrayOf('S'.code.toByte()),
                    maxResponseBytes = 256,
                    timeoutMs = 1_000,
                ),
            ),
        )
    }

    private class FakePort(
        private val response: ByteArray,
        private val sendResult: Int? = null,
    ) : UsbReadOnlyBulkPort {
        var lastRequest: ByteArray = byteArrayOf()
        var lastTimeoutMs: Int = -1

        override fun sendRequest(
            request: ByteArray,
            timeoutMs: Int,
        ): Int {
            lastRequest = request.copyOf()
            lastTimeoutMs = timeoutMs
            return sendResult ?: request.size
        }

        override fun receiveResponse(
            buffer: ByteArray,
            timeoutMs: Int,
        ): Int {
            response.copyInto(buffer)
            return response.size
        }

        override fun close() = Unit
    }
}
