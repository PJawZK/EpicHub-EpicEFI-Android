package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyBulkPort
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbGuardedBurnExecutorTest {
    @Test
    fun successfulBurnRequiresBurnAckThenPostBurnCrcVerification() {
        val authority = FakeAuthority()
        val port = FakePort()
        val channel = UsbReadOnlyTransactionChannel(port)
        val executor = UsbGuardedBurnExecutor(authority)

        assertEquals(
            UsbGuardedBurnResult.PersistedAndVerified,
            executor.execute(channel, timeoutMs = 250),
        )
        assertEquals(
            listOf(
                "stage:Burn",
                "verify:Burn",
                "stage:Crc",
                "verify:Crc",
            ),
            authority.events,
        )
        assertEquals(
            listOf(
                authority.burnRequest.toList(),
                authority.crcRequest.toList(),
            ),
            port.requests.map(ByteArray::toList),
        )
    }

    @Test
    fun rejectedBurnAckStopsBeforeCrcStageOrTransfer() {
        val authority = FakeAuthority().apply {
            verifyRejections[GuardedBurnStage.Burn] = GuardedBurnRejection.BurnProtocolFailure
        }
        val port = FakePort()
        val executor = UsbGuardedBurnExecutor(authority)

        assertEquals(
            UsbGuardedBurnResult.AuthorityRejected(
                stage = GuardedBurnStage.Burn,
                reason = GuardedBurnRejection.BurnProtocolFailure,
            ),
            executor.execute(UsbReadOnlyTransactionChannel(port), timeoutMs = 250),
        )
        assertEquals(listOf("stage:Burn", "verify:Burn"), authority.events)
        assertEquals(1, port.requests.size)
    }

    @Test
    fun crcMismatchIsNotReportedAsPersistenceSuccess() {
        val authority = FakeAuthority().apply {
            verifyRejections[GuardedBurnStage.Crc] = GuardedBurnRejection.CrcVerificationFailure
        }
        val executor = UsbGuardedBurnExecutor(authority)

        assertEquals(
            UsbGuardedBurnResult.AuthorityRejected(
                stage = GuardedBurnStage.Crc,
                reason = GuardedBurnRejection.CrcVerificationFailure,
            ),
            executor.execute(UsbReadOnlyTransactionChannel(FakePort()), timeoutMs = 250),
        )
        assertEquals(
            listOf(
                "stage:Burn",
                "verify:Burn",
                "stage:Crc",
                "verify:Crc",
            ),
            authority.events,
        )
    }

    @Test
    fun burnTransportFailureStopsBeforeCrc() {
        val authority = FakeAuthority()
        val port = FakePort().apply { failSendAt = 0 }
        val result =
            UsbGuardedBurnExecutor(authority).execute(
                UsbReadOnlyTransactionChannel(port),
                timeoutMs = 250,
            )

        assertTrue(result is UsbGuardedBurnResult.TransportFailure)
        result as UsbGuardedBurnResult.TransportFailure
        assertEquals(GuardedBurnStage.Burn, result.stage)
        assertEquals(listOf("stage:Burn"), authority.events)
        assertEquals(1, port.requests.size)
    }

    @Test
    fun invalidTimeoutFailsBeforeNativeAuthorityOrUsb() {
        val authority = FakeAuthority()
        val port = FakePort()

        assertEquals(
            UsbGuardedBurnResult.AuthorityRejected(
                stage = null,
                reason = GuardedBurnRejection.InvalidArgument,
            ),
            UsbGuardedBurnExecutor(authority).execute(
                UsbReadOnlyTransactionChannel(port),
                timeoutMs = 0,
            ),
        )
        assertTrue(authority.events.isEmpty())
        assertTrue(port.requests.isEmpty())
    }

    @Test
    fun serializerSpansBothPhysicalStagesAndNativeVerification() {
        val authority = FakeAuthority()
        val port = FakePort()
        var insideSerializer = false
        var serializerEntries = 0
        authority.insideSerializer = { insideSerializer }
        port.insideSerializer = { insideSerializer }

        val executor =
            UsbGuardedBurnExecutor(
                authority = authority,
                serializeExecution = { block ->
                    serializerEntries += 1
                    assertFalse(insideSerializer)
                    insideSerializer = true
                    try {
                        block()
                    } finally {
                        insideSerializer = false
                    }
                },
            )

        assertEquals(
            UsbGuardedBurnResult.PersistedAndVerified,
            executor.execute(UsbReadOnlyTransactionChannel(port), timeoutMs = 250),
        )
        assertEquals(1, serializerEntries)
        assertFalse(insideSerializer)
        assertTrue(authority.serializerObservations.all { it })
        assertTrue(port.serializerObservations.all { it })
    }

    private class FakeAuthority : GuardedBurnStageAuthority {
        val burnRequest = byteArrayOf(0x01, 0x42)
        val crcRequest = byteArrayOf(0x02, 0x4b)
        val events = mutableListOf<String>()
        val verifyRejections = mutableMapOf<GuardedBurnStage, GuardedBurnRejection>()
        var insideSerializer: () -> Boolean = { true }
        val serializerObservations = mutableListOf<Boolean>()

        override fun stagePlan(stage: GuardedBurnStage): GuardedBurnStageResult {
            events += "stage:$stage"
            serializerObservations += insideSerializer()
            val request =
                when (stage) {
                    GuardedBurnStage.Burn -> burnRequest
                    GuardedBurnStage.Crc -> crcRequest
                }
            val responseBytes =
                when (stage) {
                    GuardedBurnStage.Burn -> 7
                    GuardedBurnStage.Crc -> 11
                }
            return GuardedBurnStageResult.Planned(
                GuardedBurnStagePlan(
                    request = request,
                    expectedResponseBytes = responseBytes,
                ),
            )
        }

        override fun verifyStage(
            stage: GuardedBurnStage,
            responseFrame: ByteArray,
        ): GuardedBurnVerifyResult {
            events += "verify:$stage"
            serializerObservations += insideSerializer()
            return verifyRejections[stage]?.let(GuardedBurnVerifyResult::Rejected)
                ?: GuardedBurnVerifyResult.Verified
        }
    }

    private class FakePort : UsbReadOnlyBulkPort {
        val requests = mutableListOf<ByteArray>()
        private var responseIndex = 0
        var failSendAt: Int? = null
        var insideSerializer: () -> Boolean = { true }
        val serializerObservations = mutableListOf<Boolean>()

        override fun sendRequest(
            request: ByteArray,
            timeoutMs: Int,
        ): Int {
            serializerObservations += insideSerializer()
            requests += request.copyOf()
            val index = requests.lastIndex
            return if (failSendAt == index) request.size - 1 else request.size
        }

        override fun receiveResponse(
            buffer: ByteArray,
            timeoutMs: Int,
        ): Int {
            serializerObservations += insideSerializer()
            val fill = (responseIndex + 1).toByte()
            buffer.fill(fill)
            responseIndex += 1
            return buffer.size
        }

        override fun close() = Unit
    }
}
