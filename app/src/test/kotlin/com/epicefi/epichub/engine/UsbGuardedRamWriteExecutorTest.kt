package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyBulkPort
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbGuardedRamWriteExecutorTest {
    private val intent =
        GuardedRamWriteIntent(
            page = 1,
            offset = 10,
            before = byteArrayOf(1, 2),
            after = byteArrayOf(3, 4),
        )

    private val transfer =
        GuardedRamWriteTransferPlan(
            precondition = GuardedRamWriteStagePlan(byteArrayOf(0x11), 2),
            write = GuardedRamWriteStagePlan(byteArrayOf(0x22), 1),
            readback = GuardedRamWriteStagePlan(byteArrayOf(0x33), 2),
        )

    @Test
    fun successfulExecutionIsStrictlyPreconditionWriteReadback() {
        val authority = FakeAuthority(listOf(transfer))
        val port =
            RecordingPort(
                responses =
                    ArrayDeque(
                        listOf(
                            byteArrayOf(1, 2),
                            byteArrayOf(0),
                            byteArrayOf(3, 4),
                        ),
                    ),
            )
        val result =
            UsbGuardedRamWriteExecutor(authority).execute(
                signatureResponse = "exact-signature".encodeToByteArray(),
                signatureLimit = 64,
                intent = intent,
                channel = UsbReadOnlyTransactionChannel(port),
                timeoutMs = 100,
            )

        assertEquals(UsbGuardedRamWriteResult.Reconciled(1), result)
        assertEquals(
            listOf(byteArrayOf(0x11), byteArrayOf(0x22), byteArrayOf(0x33)).map { it.toList() },
            port.sentRequests.map { it.toList() },
        )
        assertEquals(listOf("pre:0", "ack:0", "readback:0"), authority.verifications)
    }

    @Test
    fun stalePreconditionNeverSendsWriteRequest() {
        val authority =
            FakeAuthority(
                transfers = listOf(transfer),
                preconditionResult =
                    GuardedRamWriteVerifyResult.Rejected(
                        GuardedRamWriteAuthorityRejection.StalePrecondition,
                    ),
            )
        val port = RecordingPort(ArrayDeque(listOf(byteArrayOf(9, 9))))

        val result = execute(authority, port)

        assertEquals(
            UsbGuardedRamWriteResult.AuthorityRejected(
                stage = GuardedRamWriteStage.Precondition,
                planIndex = 0,
                reason = GuardedRamWriteAuthorityRejection.StalePrecondition,
            ),
            result,
        )
        assertEquals(listOf(listOf(0x11.toByte())), port.sentRequests.map { it.toList() })
        assertEquals(listOf("pre:0"), authority.verifications)
    }

    @Test
    fun acknowledgementRejectionNeverSendsReadbackRequest() {
        val authority =
            FakeAuthority(
                transfers = listOf(transfer),
                acknowledgementResult =
                    GuardedRamWriteVerifyResult.Rejected(
                        GuardedRamWriteAuthorityRejection.WriteAcknowledgementFailure,
                    ),
            )
        val port = RecordingPort(ArrayDeque(listOf(byteArrayOf(1, 2), byteArrayOf(0))))

        val result = execute(authority, port)

        assertEquals(
            UsbGuardedRamWriteResult.AuthorityRejected(
                stage = GuardedRamWriteStage.Write,
                planIndex = 0,
                reason = GuardedRamWriteAuthorityRejection.WriteAcknowledgementFailure,
            ),
            result,
        )
        assertEquals(
            listOf(byteArrayOf(0x11), byteArrayOf(0x22)).map { it.toList() },
            port.sentRequests.map { it.toList() },
        )
        assertEquals(listOf("pre:0", "ack:0"), authority.verifications)
    }

    @Test
    fun readbackMismatchIsReportedAfterWriteButNeverAsReconciled() {
        val authority =
            FakeAuthority(
                transfers = listOf(transfer),
                readbackResult =
                    GuardedRamWriteVerifyResult.Rejected(
                        GuardedRamWriteAuthorityRejection.ReadbackMismatch,
                    ),
            )
        val port =
            RecordingPort(
                ArrayDeque(
                    listOf(byteArrayOf(1, 2), byteArrayOf(0), byteArrayOf(3, 9)),
                ),
            )

        val result = execute(authority, port)

        assertEquals(
            UsbGuardedRamWriteResult.AuthorityRejected(
                stage = GuardedRamWriteStage.Readback,
                planIndex = 0,
                reason = GuardedRamWriteAuthorityRejection.ReadbackMismatch,
            ),
            result,
        )
        assertEquals(3, port.sentRequests.size)
        assertEquals(listOf("pre:0", "ack:0", "readback:0"), authority.verifications)
    }

    @Test
    fun writeTransportFailureStopsBeforeAcknowledgementAndReadback() {
        val authority = FakeAuthority(listOf(transfer))
        val port =
            RecordingPort(
                responses = ArrayDeque(listOf(byteArrayOf(1, 2))),
                failSendRequest = byteArrayOf(0x22),
            )

        val result = execute(authority, port)

        assertTrue(result is UsbGuardedRamWriteResult.TransportFailure)
        result as UsbGuardedRamWriteResult.TransportFailure
        assertEquals(GuardedRamWriteStage.Write, result.stage)
        assertEquals(0, result.planIndex)
        assertEquals(
            listOf(byteArrayOf(0x11), byteArrayOf(0x22)).map { it.toList() },
            port.sentRequests.map { it.toList() },
        )
        assertEquals(listOf("pre:0"), authority.verifications)
    }

    @Test
    fun planningRejectionPerformsNoTransportIo() {
        val authority =
            FakeAuthority(
                transfers = emptyList(),
                planningRejection = GuardedRamWriteAuthorityRejection.MissingCompiledBundle,
            )
        val port = RecordingPort(ArrayDeque())

        val result = execute(authority, port)

        assertEquals(
            UsbGuardedRamWriteResult.AuthorityRejected(
                stage = null,
                planIndex = null,
                reason = GuardedRamWriteAuthorityRejection.MissingCompiledBundle,
            ),
            result,
        )
        assertTrue(port.sentRequests.isEmpty())
    }

    private fun execute(
        authority: FakeAuthority,
        port: RecordingPort,
    ): UsbGuardedRamWriteResult =
        UsbGuardedRamWriteExecutor(authority).execute(
            signatureResponse = "exact-signature".encodeToByteArray(),
            signatureLimit = 64,
            intent = intent,
            channel = UsbReadOnlyTransactionChannel(port),
            timeoutMs = 100,
        )

    private class FakeAuthority(
        private val transfers: List<GuardedRamWriteTransferPlan>,
        private val planningRejection: GuardedRamWriteAuthorityRejection? = null,
        private val preconditionResult: GuardedRamWriteVerifyResult = GuardedRamWriteVerifyResult.Verified,
        private val acknowledgementResult: GuardedRamWriteVerifyResult = GuardedRamWriteVerifyResult.Verified,
        private val readbackResult: GuardedRamWriteVerifyResult = GuardedRamWriteVerifyResult.Verified,
    ) : GuardedRamWriteAuthority {
        val verifications = mutableListOf<String>()

        override fun plan(
            signatureResponse: ByteArray,
            signatureLimit: Int,
            intent: GuardedRamWriteIntent,
        ): GuardedRamWritePlanResult =
            planningRejection?.let(GuardedRamWritePlanResult::Rejected)
                ?: GuardedRamWritePlanResult.Planned(transfers)

        override fun verifyPrecondition(
            signatureResponse: ByteArray,
            signatureLimit: Int,
            intent: GuardedRamWriteIntent,
            planIndex: Int,
            responseFrame: ByteArray,
        ): GuardedRamWriteVerifyResult {
            verifications += "pre:$planIndex"
            return preconditionResult
        }

        override fun verifyWriteAcknowledgement(
            signatureResponse: ByteArray,
            signatureLimit: Int,
            intent: GuardedRamWriteIntent,
            planIndex: Int,
            responseFrame: ByteArray,
        ): GuardedRamWriteVerifyResult {
            verifications += "ack:$planIndex"
            return acknowledgementResult
        }

        override fun verifyReadback(
            signatureResponse: ByteArray,
            signatureLimit: Int,
            intent: GuardedRamWriteIntent,
            planIndex: Int,
            responseFrame: ByteArray,
        ): GuardedRamWriteVerifyResult {
            verifications += "readback:$planIndex"
            return readbackResult
        }
    }

    private class RecordingPort(
        private val responses: ArrayDeque<ByteArray>,
        private val failSendRequest: ByteArray? = null,
    ) : UsbReadOnlyBulkPort {
        val sentRequests = mutableListOf<ByteArray>()

        override fun sendRequest(
            request: ByteArray,
            timeoutMs: Int,
        ): Int {
            sentRequests += request.copyOf()
            if (failSendRequest?.contentEquals(request) == true) {
                return -1
            }
            return request.size
        }

        override fun receiveResponse(
            buffer: ByteArray,
            timeoutMs: Int,
        ): Int {
            if (responses.isEmpty()) {
                return -1
            }
            val response = responses.removeFirst()
            response.copyInto(buffer)
            return response.size
        }

        override fun close() = Unit
    }
}
