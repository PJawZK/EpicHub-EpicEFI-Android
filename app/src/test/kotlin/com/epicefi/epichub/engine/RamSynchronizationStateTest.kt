package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RamSynchronizationStateTest {
    @Test
    fun freshLiveControllerConfirmationCarriesExactBaselineEvidence() {
        val state =
            decodeRamSynchronizationStateSnapshot(
                snapshot(
                    status = 1,
                    flags = 0x01,
                    currentDraftRevision = 0,
                    confirmedDraftRevision = 0,
                ),
            )

        assertEquals(RamSynchronizationStatus.FreshLiveControllerConfirmation, state.status)
        assertEquals(77, state.profileGeneration)
        assertEquals(0, state.currentDraftRevision)
        assertEquals(0, state.confirmedDraftRevision)
        assertEquals(RamSourceAcquisition.LiveControllerRead, state.acquisition)
        assertNull(state.lastReconciliation)
    }

    @Test
    fun localDraftAheadKeepsOlderConfirmedRamRevisionExplicit() {
        val state =
            decodeRamSynchronizationStateSnapshot(
                snapshot(
                    status = 3,
                    flags = 0x01,
                    currentDraftRevision = 4,
                    confirmedDraftRevision = 2,
                    verifiedWriteRangeCount = 3,
                ),
            )

        assertEquals(RamSynchronizationStatus.LocalDraftAheadOfConfirmedRam, state.status)
        assertEquals(4, state.currentDraftRevision)
        assertEquals(2, state.confirmedDraftRevision)
        assertEquals(3, state.verifiedWriteRangeCount)
    }

    @Test
    fun completeReconciliationConfirmsExactCurrentDraft() {
        val state =
            decodeRamSynchronizationStateSnapshot(
                snapshot(
                    status = 2,
                    flags = 0x01 or 0x02 or 0x04,
                    currentDraftRevision = 4,
                    confirmedDraftRevision = 4,
                    verifiedWriteRangeCount = 2,
                    lastDraftRevision = 4,
                    lastVerifiedTransactionCount = 2,
                    lastTransactionCount = 2,
                ),
            )

        assertEquals(RamSynchronizationStatus.CurrentDraftConfirmedInRam, state.status)
        assertEquals(
            RamWriteReconciliationEvidence(
                draftRevision = 4,
                verifiedTransactionCount = 2,
                transactionCount = 2,
                complete = true,
            ),
            state.lastReconciliation,
        )
    }

    @Test
    fun interruptedWriteWithVerifiedPrefixIsExplicitlyPartial() {
        val state =
            decodeRamSynchronizationStateSnapshot(
                snapshot(
                    status = 4,
                    flags = 0x02,
                    currentDraftRevision = 4,
                    confirmedDraftRevision = null,
                    verifiedWriteRangeCount = 1,
                    lastDraftRevision = 4,
                    lastVerifiedTransactionCount = 1,
                    lastTransactionCount = 2,
                ),
            )

        assertEquals(RamSynchronizationStatus.PartiallyVerifiedInterruptedWrite, state.status)
        assertNull(state.confirmedDraftRevision)
        assertEquals(1, state.verifiedWriteRangeCount)
        assertEquals(1, state.lastReconciliation?.verifiedTransactionCount)
        assertEquals(false, state.lastReconciliation?.complete)
    }

    @Test
    fun interruptedWriteWithZeroVerifiedPrefixIsExplicitlyUnconfirmed() {
        val state =
            decodeRamSynchronizationStateSnapshot(
                snapshot(
                    status = 5,
                    flags = 0x02,
                    currentDraftRevision = 4,
                    confirmedDraftRevision = null,
                    lastDraftRevision = 4,
                    lastVerifiedTransactionCount = 0,
                    lastTransactionCount = 1,
                ),
            )

        assertEquals(RamSynchronizationStatus.RamUnconfirmedAfterInterruptedWrite, state.status)
        assertNull(state.confirmedDraftRevision)
        assertEquals(0, state.lastReconciliation?.verifiedTransactionCount)
        assertEquals(1, state.lastReconciliation?.transactionCount)
    }

    @Test
    fun contradictoryNativeEvidenceFailsClosedInsteadOfInventingSynchronization() {
        val contradictory =
            snapshot(
                status = 2,
                flags = 0x01 or 0x02 or 0x04,
                currentDraftRevision = 4,
                confirmedDraftRevision = 4,
                lastDraftRevision = 4,
                lastVerifiedTransactionCount = 1,
                lastTransactionCount = 2,
            )

        assertThrows(IllegalArgumentException::class.java) {
            decodeRamSynchronizationStateSnapshot(contradictory)
        }
    }

    private fun snapshot(
        status: Int,
        flags: Int,
        currentDraftRevision: Long,
        confirmedDraftRevision: Long?,
        verifiedWriteRangeCount: Long = 0,
        lastDraftRevision: Long? = null,
        lastVerifiedTransactionCount: Long = 0,
        lastTransactionCount: Long = 0,
    ): ByteBuffer =
        ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(RAM_STATE_MAGIC)
            put(status.toByte())
            put(1)
            put(flags.toByte())
            put(1)
            putLong(77)
            putLong(currentDraftRevision)
            putLong(confirmedDraftRevision ?: -1)
            putLong(verifiedWriteRangeCount)
            putLong(lastDraftRevision ?: -1)
            putLong(lastVerifiedTransactionCount)
            putLong(lastTransactionCount)
            rewind()
        }
}
