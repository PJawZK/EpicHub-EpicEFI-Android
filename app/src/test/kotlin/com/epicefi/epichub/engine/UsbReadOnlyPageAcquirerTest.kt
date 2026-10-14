package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyBulkPort
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbReadOnlyPageAcquirerTest {
    @Test
    fun executes_native_plans_sequentially_and_returns_only_verified_payloads() {
        val authority =
            FakeAuthority(
                transfers =
                    listOf(
                        ReadOnlyPageTransferPlan(byteArrayOf(1, 2), 4),
                        ReadOnlyPageTransferPlan(byteArrayOf(3), 3),
                    ),
                verifiedPayloads = listOf(byteArrayOf(10, 11), byteArrayOf(12, 13, 14)),
            )
        val port = FakePort(listOf(byteArrayOf(20, 21, 22, 23), byteArrayOf(30, 31, 32)))
        val channel = UsbReadOnlyTransactionChannel(port)
        val acquirer = UsbReadOnlyPageAcquirer(authority)

        val result =
            acquirer.acquirePage(
                signatureResponse = "sig".toByteArray(),
                signatureLimit = 64,
                pageIndex = 0,
                channel = channel,
                timeoutMs = 250,
            )

        val acquired = result as UsbReadOnlyPageAcquisitionResult.Acquired
        assertArrayEquals(byteArrayOf(10, 11, 12, 13, 14), acquired.page)
        assertEquals(2, authority.verifyCalls)
        assertEquals(listOf(0, 1), authority.verifiedPlanIndices)
        assertEquals(2, port.sentRequests.size)
        assertArrayEquals(byteArrayOf(1, 2), port.sentRequests[0])
        assertArrayEquals(byteArrayOf(3), port.sentRequests[1])
        assertEquals(listOf(250, 250), port.sendTimeouts)
    }

    @Test
    fun authority_rejection_before_planning_performs_no_transport_io() {
        val authority =
            FakeAuthority(
                planRejection = ReadOnlyPageAuthorityRejection.UnknownController,
            )
        val port = FakePort(emptyList())
        val result =
            UsbReadOnlyPageAcquirer(authority).acquirePage(
                "unknown".toByteArray(),
                64,
                0,
                UsbReadOnlyTransactionChannel(port),
                100,
            )

        assertEquals(
            UsbReadOnlyPageAcquisitionResult.AuthorityRejected(
                ReadOnlyPageAuthorityRejection.UnknownController,
            ),
            result,
        )
        assertTrue(port.sentRequests.isEmpty())
        assertEquals(0, authority.verifyCalls)
    }

    @Test
    fun transport_failure_stops_before_verification_and_later_transfers() {
        val authority =
            FakeAuthority(
                transfers =
                    listOf(
                        ReadOnlyPageTransferPlan(byteArrayOf(1), 4),
                        ReadOnlyPageTransferPlan(byteArrayOf(2), 4),
                    ),
                verifiedPayloads = listOf(byteArrayOf(9), byteArrayOf(8)),
            )
        val port =
            FakePort(
                responseChunks = listOf(byteArrayOf(1, 2)),
                receiveOverrides = listOf(null, 0),
            )

        val result =
            UsbReadOnlyPageAcquirer(authority).acquirePage(
                "sig".toByteArray(),
                64,
                0,
                UsbReadOnlyTransactionChannel(port),
                100,
            )

        assertTrue(result is UsbReadOnlyPageAcquisitionResult.TransportFailure)
        assertEquals(0, authority.verifyCalls)
        assertEquals(1, port.sentRequests.size)
    }

    @Test
    fun native_response_rejection_stops_before_next_transfer() {
        val authority =
            FakeAuthority(
                transfers =
                    listOf(
                        ReadOnlyPageTransferPlan(byteArrayOf(1), 2),
                        ReadOnlyPageTransferPlan(byteArrayOf(2), 2),
                    ),
                verifyRejectionAt = 0,
                verifyRejection = ReadOnlyPageAuthorityRejection.EnvelopeFailure,
            )
        val port = FakePort(listOf(byteArrayOf(1, 2), byteArrayOf(3, 4)))

        val result =
            UsbReadOnlyPageAcquirer(authority).acquirePage(
                "sig".toByteArray(),
                64,
                0,
                UsbReadOnlyTransactionChannel(port),
                100,
            )

        assertEquals(
            UsbReadOnlyPageAcquisitionResult.AuthorityRejected(
                ReadOnlyPageAuthorityRejection.EnvelopeFailure,
            ),
            result,
        )
        assertEquals(1, authority.verifyCalls)
        assertEquals(1, port.sentRequests.size)
    }

    @Test
    fun invalid_timeout_is_rejected_before_native_or_transport_work() {
        val authority = FakeAuthority(transfers = listOf(ReadOnlyPageTransferPlan(byteArrayOf(1), 1)))
        val port = FakePort(emptyList())

        val result =
            UsbReadOnlyPageAcquirer(authority).acquirePage(
                "sig".toByteArray(),
                64,
                0,
                UsbReadOnlyTransactionChannel(port),
                0,
            )

        assertEquals(
            UsbReadOnlyPageAcquisitionResult.TransportRejected("timeout must be positive"),
            result,
        )
        assertEquals(0, authority.planCalls)
        assertTrue(port.sentRequests.isEmpty())
    }

    private class FakeAuthority(
        private val transfers: List<ReadOnlyPageTransferPlan> = emptyList(),
        private val verifiedPayloads: List<ByteArray> = emptyList(),
        private val planRejection: ReadOnlyPageAuthorityRejection? = null,
        private val verifyRejectionAt: Int? = null,
        private val verifyRejection: ReadOnlyPageAuthorityRejection =
            ReadOnlyPageAuthorityRejection.NativeFailure,
    ) : ReadOnlyPageAuthority {
        var planCalls = 0
        var verifyCalls = 0
        val verifiedPlanIndices = mutableListOf<Int>()

        override fun planPage(
            signatureResponse: ByteArray,
            signatureLimit: Int,
            pageIndex: Int,
        ): ReadOnlyPagePlanResult {
            planCalls += 1
            if (planRejection != null) {
                return ReadOnlyPagePlanResult.Rejected(planRejection)
            }
            return ReadOnlyPagePlanResult.Planned(transfers)
        }

        override fun verifyChunk(
            signatureResponse: ByteArray,
            signatureLimit: Int,
            pageIndex: Int,
            planIndex: Int,
            responseFrame: ByteArray,
        ): ReadOnlyPageVerifyResult {
            verifyCalls += 1
            verifiedPlanIndices += planIndex
            if (verifyRejectionAt == planIndex) {
                return ReadOnlyPageVerifyResult.Rejected(verifyRejection)
            }
            return ReadOnlyPageVerifyResult.Verified(verifiedPayloads[planIndex])
        }
    }

    private class FakePort(
        private val responseChunks: List<ByteArray>,
        private val receiveOverrides: List<Int?> = emptyList(),
    ) : UsbReadOnlyBulkPort {
        val sentRequests = mutableListOf<ByteArray>()
        val sendTimeouts = mutableListOf<Int>()
        private var receiveIndex = 0

        override fun sendRequest(
            request: ByteArray,
            timeoutMs: Int,
        ): Int {
            sentRequests += request.copyOf()
            sendTimeouts += timeoutMs
            return request.size
        }

        override fun receiveResponse(
            buffer: ByteArray,
            timeoutMs: Int,
        ): Int {
            val index = receiveIndex++
            val override = receiveOverrides.getOrNull(index)
            if (override != null) {
                return override
            }
            val chunk = responseChunks.getOrElse(index) { byteArrayOf() }
            chunk.copyInto(buffer, endIndex = minOf(chunk.size, buffer.size))
            return chunk.size
        }

        override fun close() = Unit
    }
}
