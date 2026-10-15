package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyBulkPort
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbNativeGuardedWriteBatchExecutorTest {
    @Test
    fun zeroTransactionBatchIsSuccessfulNoOp() {
        val events = mutableListOf<String>()
        val authority = FakeBatchAuthority(transactionCount = 0, events = events)
        val port = RecordingPort(ArrayDeque(), events = events)

        val result = execute(authority, port)

        assertEquals(UsbNativeGuardedWriteBatchResult.Reconciled(0), result)
        assertTrue(port.sentRequests.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun stagesAreAuthorizedJustInTimeAndExecutedInStrictOrder() {
        val events = mutableListOf<String>()
        val authority = FakeBatchAuthority(transactionCount = 1, events = events)
        val port =
            RecordingPort(
                responses = ArrayDeque(listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3))),
                events = events,
            )

        val result = execute(authority, port)

        assertEquals(UsbNativeGuardedWriteBatchResult.Reconciled(1), result)
        assertEquals(
            listOf(
                "plan:0:Precondition",
                "send:11",
                "receive:1",
                "verify:0:Precondition",
                "plan:0:Write",
                "send:22",
                "receive:2",
                "verify:0:Write",
                "plan:0:Readback",
                "send:33",
                "receive:3",
                "verify:0:Readback",
            ),
            events,
        )
    }

    @Test
    fun staleDraftBeforeWritePreventsWriteTransport() {
        val events = mutableListOf<String>()
        val authority =
            FakeBatchAuthority(
                transactionCount = 1,
                events = events,
                planRejections =
                    mapOf(
                        GuardedRamWriteStage.Write to GuardedWriteBatchRejection.StaleDraftRevision,
                    ),
            )
        val port = RecordingPort(ArrayDeque(listOf(byteArrayOf(1))), events = events)

        val result = execute(authority, port)

        assertEquals(
            UsbNativeGuardedWriteBatchResult.AuthorityRejected(
                stage = GuardedRamWriteStage.Write,
                transactionIndex = 0,
                reason = GuardedWriteBatchRejection.StaleDraftRevision,
            ),
            result,
        )
        assertEquals(listOf(listOf(0x11.toByte())), port.sentRequests.map { it.toList() })
        assertTrue(events.none { it == "send:22" })
    }

    @Test
    fun sourceChangeBeforeReadbackPreventsReadbackTransport() {
        val events = mutableListOf<String>()
        val authority =
            FakeBatchAuthority(
                transactionCount = 1,
                events = events,
                planRejections =
                    mapOf(
                        GuardedRamWriteStage.Readback to GuardedWriteBatchRejection.SourceImageChanged,
                    ),
            )
        val port = RecordingPort(ArrayDeque(listOf(byteArrayOf(1), byteArrayOf(2))), events = events)

        val result = execute(authority, port)

        assertEquals(
            UsbNativeGuardedWriteBatchResult.AuthorityRejected(
                stage = GuardedRamWriteStage.Readback,
                transactionIndex = 0,
                reason = GuardedWriteBatchRejection.SourceImageChanged,
            ),
            result,
        )
        assertEquals(
            listOf(byteArrayOf(0x11), byteArrayOf(0x22)).map { it.toList() },
            port.sentRequests.map { it.toList() },
        )
    }

    @Test
    fun staleEcuPreconditionPreventsWritePlanningAndTransport() {
        val events = mutableListOf<String>()
        val authority =
            FakeBatchAuthority(
                transactionCount = 1,
                events = events,
                verifyRejections =
                    mapOf(
                        GuardedRamWriteStage.Precondition to GuardedWriteBatchRejection.StalePrecondition,
                    ),
            )
        val port = RecordingPort(ArrayDeque(listOf(byteArrayOf(9))), events = events)

        val result = execute(authority, port)

        assertEquals(
            UsbNativeGuardedWriteBatchResult.AuthorityRejected(
                stage = GuardedRamWriteStage.Precondition,
                transactionIndex = 0,
                reason = GuardedWriteBatchRejection.StalePrecondition,
            ),
            result,
        )
        assertEquals(listOf(listOf(0x11.toByte())), port.sentRequests.map { it.toList() })
        assertTrue(events.none { it == "plan:0:Write" })
    }

    @Test
    fun writeAckFailurePreventsReadbackPlanning() {
        val events = mutableListOf<String>()
        val authority =
            FakeBatchAuthority(
                transactionCount = 1,
                events = events,
                verifyRejections =
                    mapOf(
                        GuardedRamWriteStage.Write to
                            GuardedWriteBatchRejection.WriteAcknowledgementFailure,
                    ),
            )
        val port = RecordingPort(ArrayDeque(listOf(byteArrayOf(1), byteArrayOf(2))), events = events)

        val result = execute(authority, port)

        assertEquals(
            UsbNativeGuardedWriteBatchResult.AuthorityRejected(
                stage = GuardedRamWriteStage.Write,
                transactionIndex = 0,
                reason = GuardedWriteBatchRejection.WriteAcknowledgementFailure,
            ),
            result,
        )
        assertTrue(events.none { it == "plan:0:Readback" })
    }

    @Test
    fun readbackMismatchNeverReportsReconciled() {
        val events = mutableListOf<String>()
        val authority =
            FakeBatchAuthority(
                transactionCount = 1,
                events = events,
                verifyRejections =
                    mapOf(
                        GuardedRamWriteStage.Readback to GuardedWriteBatchRejection.ReadbackMismatch,
                    ),
            )
        val port =
            RecordingPort(
                ArrayDeque(listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(9))),
                events = events,
            )

        val result = execute(authority, port)

        assertEquals(
            UsbNativeGuardedWriteBatchResult.AuthorityRejected(
                stage = GuardedRamWriteStage.Readback,
                transactionIndex = 0,
                reason = GuardedWriteBatchRejection.ReadbackMismatch,
            ),
            result,
        )
    }

    @Test
    fun transportFailureStopsAllLaterAuthorityWork() {
        val events = mutableListOf<String>()
        val authority = FakeBatchAuthority(transactionCount = 1, events = events)
        val port =
            RecordingPort(
                responses = ArrayDeque(listOf(byteArrayOf(1))),
                failSendRequest = byteArrayOf(0x22),
                events = events,
            )

        val result = execute(authority, port)

        assertTrue(result is UsbNativeGuardedWriteBatchResult.TransportFailure)
        result as UsbNativeGuardedWriteBatchResult.TransportFailure
        assertEquals(GuardedRamWriteStage.Write, result.stage)
        assertEquals(0, result.transactionIndex)
        assertTrue(events.none { it == "verify:0:Write" })
        assertTrue(events.none { it == "plan:0:Readback" })
    }

    @Test
    fun invalidTimeoutFailsBeforeAuthorityOrTransportWork() {
        val events = mutableListOf<String>()
        val authority = FakeBatchAuthority(transactionCount = 1, events = events)
        val port = RecordingPort(ArrayDeque(), events = events)

        val result =
            UsbNativeGuardedWriteBatchExecutor(authority).execute(
                channel = UsbReadOnlyTransactionChannel(port),
                timeoutMs = 0,
            )

        assertEquals(
            UsbNativeGuardedWriteBatchResult.AuthorityRejected(
                stage = null,
                transactionIndex = null,
                reason = GuardedWriteBatchRejection.InvalidArgument,
            ),
            result,
        )
        assertTrue(events.isEmpty())
    }

    private fun execute(
        authority: FakeBatchAuthority,
        port: RecordingPort,
    ): UsbNativeGuardedWriteBatchResult =
        UsbNativeGuardedWriteBatchExecutor(authority).execute(
            channel = UsbReadOnlyTransactionChannel(port),
            timeoutMs = 100,
        )

    private class FakeBatchAuthority(
        override val transactionCount: Int,
        private val events: MutableList<String>,
        private val planRejections: Map<GuardedRamWriteStage, GuardedWriteBatchRejection> = emptyMap(),
        private val verifyRejections: Map<GuardedRamWriteStage, GuardedWriteBatchRejection> = emptyMap(),
    ) : GuardedWriteBatchStageAuthority {
        override fun stagePlan(
            transactionIndex: Int,
            stage: GuardedRamWriteStage,
        ): GuardedWriteBatchStageResult {
            events += "plan:$transactionIndex:$stage"
            planRejections[stage]?.let {
                return GuardedWriteBatchStageResult.Rejected(it)
            }
            val (request, expected) =
                when (stage) {
                    GuardedRamWriteStage.Precondition -> byteArrayOf(0x11) to 1
                    GuardedRamWriteStage.Write -> byteArrayOf(0x22) to 1
                    GuardedRamWriteStage.Readback -> byteArrayOf(0x33) to 1
                }
            return GuardedWriteBatchStageResult.Planned(
                GuardedRamWriteStagePlan(request, expected),
            )
        }

        override fun verifyStage(
            transactionIndex: Int,
            stage: GuardedRamWriteStage,
            responseFrame: ByteArray,
        ): GuardedWriteBatchVerifyResult {
            events += "verify:$transactionIndex:$stage"
            verifyRejections[stage]?.let {
                return GuardedWriteBatchVerifyResult.Rejected(it)
            }
            return GuardedWriteBatchVerifyResult.Verified
        }
    }

    private class RecordingPort(
        private val responses: ArrayDeque<ByteArray>,
        private val failSendRequest: ByteArray? = null,
        private val events: MutableList<String>,
    ) : UsbReadOnlyBulkPort {
        val sentRequests = mutableListOf<ByteArray>()

        override fun sendRequest(
            request: ByteArray,
            timeoutMs: Int,
        ): Int {
            sentRequests += request.copyOf()
            events += "send:${request.first().toString(16)}"
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
            events += "receive:${response.first().toString(16)}"
            return response.size
        }

        override fun close() = Unit
    }
}
