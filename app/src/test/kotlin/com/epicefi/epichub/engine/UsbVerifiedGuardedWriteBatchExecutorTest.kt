package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyBulkPort
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class UsbVerifiedGuardedWriteBatchExecutorTest {
    private val unusedChannel = UsbReadOnlyTransactionChannel(UnusedPort())

    @Test
    fun fullTransportSuccessReconcilesExactFullPrefix() {
        val reconciled = mutableListOf<Int>()
        val executor =
            executor(
                transportResult = UsbNativeGuardedWriteBatchResult.Reconciled(3),
                reconciled = reconciled,
            )

        val result = executor.execute(unusedChannel, 100)

        assertEquals(UsbVerifiedGuardedWriteBatchResult.Reconciled(3), result)
        assertEquals(listOf(3), reconciled)
    }

    @Test
    fun interruptedSecondTransactionReconcilesOnlyFirstVerifiedTransaction() {
        val failure =
            UsbNativeGuardedWriteBatchResult.TransportFailure(
                stage = GuardedRamWriteStage.Write,
                transactionIndex = 1,
                reason = "usb write failed",
            )
        val reconciled = mutableListOf<Int>()
        val executor = executor(failure, reconciled)

        val result = executor.execute(unusedChannel, 100)

        assertEquals(
            UsbVerifiedGuardedWriteBatchResult.Interrupted(
                reconciledTransactionCount = 1,
                cause = failure,
            ),
            result,
        )
        assertEquals(listOf(1), reconciled)
    }

    @Test
    fun interruptedFirstTransactionReconcilesZeroPrefixForProvenanceInvalidation() {
        val failure =
            UsbNativeGuardedWriteBatchResult.AuthorityRejected(
                stage = GuardedRamWriteStage.Readback,
                transactionIndex = 0,
                reason = GuardedWriteBatchRejection.ReadbackMismatch,
            )
        val reconciled = mutableListOf<Int>()
        val executor = executor(failure, reconciled)

        val result = executor.execute(unusedChannel, 100)

        assertEquals(
            UsbVerifiedGuardedWriteBatchResult.Interrupted(
                reconciledTransactionCount = 0,
                cause = failure,
            ),
            result,
        )
        assertEquals(listOf(0), reconciled)
    }

    @Test
    fun zeroPrefixReconciliationFailurePreservesInterruptedCause() {
        val failure =
            UsbNativeGuardedWriteBatchResult.TransportFailure(
                stage = GuardedRamWriteStage.Write,
                transactionIndex = 0,
                reason = "usb write failed",
            )
        val result =
            UsbVerifiedGuardedWriteBatchExecutor(
                executeTransport = { _, _ -> failure },
                reconcileVerifiedPrefix = { count ->
                    assertEquals(0, count)
                    GuardedWriteBatchReconciliationResult.Rejected(
                        GuardedWriteBatchReconciliationRejection.SourceReconciliationFailure,
                    )
                },
            ).execute(unusedChannel, 100)

        result as UsbVerifiedGuardedWriteBatchResult.ReconciliationRejected
        assertEquals(0, result.verifiedTransactionCount)
        assertEquals(
            GuardedWriteBatchReconciliationRejection.SourceReconciliationFailure,
            result.reason,
        )
        assertSame(failure, result.interruptedCause)
    }

    @Test
    fun reconciliationFailureNeverReportsTransportSuccessAsSynchronized() {
        val transport = UsbNativeGuardedWriteBatchResult.Reconciled(2)
        val result =
            UsbVerifiedGuardedWriteBatchExecutor(
                executeTransport = { _, _ -> transport },
                reconcileVerifiedPrefix = {
                    GuardedWriteBatchReconciliationResult.Rejected(
                        GuardedWriteBatchReconciliationRejection.SourceImageChanged,
                    )
                },
            ).execute(unusedChannel, 100)

        result as UsbVerifiedGuardedWriteBatchResult.ReconciliationRejected
        assertEquals(2, result.verifiedTransactionCount)
        assertEquals(GuardedWriteBatchReconciliationRejection.SourceImageChanged, result.reason)
        assertNull(result.interruptedCause)
    }

    @Test
    fun reconciliationFailurePreservesOriginalInterruptedTransportResult() {
        val failure =
            UsbNativeGuardedWriteBatchResult.TransportRejected(
                stage = GuardedRamWriteStage.Precondition,
                transactionIndex = 2,
                reason = "short response",
            )
        val result =
            UsbVerifiedGuardedWriteBatchExecutor(
                executeTransport = { _, _ -> failure },
                reconcileVerifiedPrefix = {
                    GuardedWriteBatchReconciliationResult.Rejected(
                        GuardedWriteBatchReconciliationRejection.StaleDraftRevision,
                    )
                },
            ).execute(unusedChannel, 100)

        result as UsbVerifiedGuardedWriteBatchResult.ReconciliationRejected
        assertEquals(2, result.verifiedTransactionCount)
        assertEquals(GuardedWriteBatchReconciliationRejection.StaleDraftRevision, result.reason)
        assertSame(failure, result.interruptedCause)
    }

    @Test
    fun serializerCoversTransportAndVerifiedSourceReconciliationAsOneCriticalSection() {
        val events = mutableListOf<String>()
        val executor =
            UsbVerifiedGuardedWriteBatchExecutor(
                executeTransport = { _, _ ->
                    events += "transport"
                    UsbNativeGuardedWriteBatchResult.Reconciled(1)
                },
                reconcileVerifiedPrefix = {
                    events += "reconcile"
                    GuardedWriteBatchReconciliationResult.Reconciled
                },
                serializeExecution = { block ->
                    events += "enter"
                    val result = block()
                    events += "exit"
                    result
                },
            )

        assertEquals(
            UsbVerifiedGuardedWriteBatchResult.Reconciled(1),
            executor.execute(unusedChannel, 100),
        )
        assertEquals(listOf("enter", "transport", "reconcile", "exit"), events)
    }

    private fun executor(
        transportResult: UsbNativeGuardedWriteBatchResult,
        reconciled: MutableList<Int>,
    ): UsbVerifiedGuardedWriteBatchExecutor =
        UsbVerifiedGuardedWriteBatchExecutor(
            executeTransport = { _, _ -> transportResult },
            reconcileVerifiedPrefix = { count ->
                reconciled += count
                GuardedWriteBatchReconciliationResult.Reconciled
            },
        )

    private class UnusedPort : UsbReadOnlyBulkPort {
        override fun sendRequest(
            request: ByteArray,
            timeoutMs: Int,
        ): Int = throw AssertionError("transport should be injected")

        override fun receiveResponse(
            buffer: ByteArray,
            timeoutMs: Int,
        ): Int = throw AssertionError("transport should be injected")

        override fun close() = Unit
    }
}
