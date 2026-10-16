package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel

internal sealed interface LiveTuneRamWriteResult {
    data class Reconciled(
        val transactionCount: Int,
        val ramState: RamSynchronizationState,
    ) : LiveTuneRamWriteResult

    data class Interrupted(
        val reconciledTransactionCount: Int,
        val cause: UsbNativeGuardedWriteBatchResult,
        val ramState: RamSynchronizationState,
    ) : LiveTuneRamWriteResult

    data class ReconciliationRejected(
        val verifiedTransactionCount: Int,
        val reason: GuardedWriteBatchReconciliationRejection,
        val interruptedCause: UsbNativeGuardedWriteBatchResult?,
        val ramState: RamSynchronizationState?,
    ) : LiveTuneRamWriteResult

    data class BatchRejected(
        val reason: GuardedWriteBatchRejection,
        val ramState: RamSynchronizationState?,
    ) : LiveTuneRamWriteResult

    data object RamStateUnavailable : LiveTuneRamWriteResult
}

internal fun interface LiveTuneRamWriteOperation {
    fun write(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): LiveTuneRamWriteResult
}

internal class NativeLiveTuneRamWriteOperation(
    private val engine: NativeEngine,
    private val registry: NativeProfileRegistry,
) : LiveTuneRamWriteOperation {
    override fun write(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): LiveTuneRamWriteResult {
        if (signatureLimit <= 0 || timeoutMs <= 0) {
            return LiveTuneRamWriteResult.BatchRejected(
                reason = GuardedWriteBatchRejection.InvalidArgument,
                ramState = readRamStateOrNull(),
            )
        }

        val catalog = engine.readTuneCatalog()
        if (catalog.origin != TuneSourceOrigin.LIVE_CONTROLLER) {
            return LiveTuneRamWriteResult.BatchRejected(
                reason = GuardedWriteBatchRejection.NonLiveControllerSource,
                ramState = readRamStateOrNull(),
            )
        }

        return when (
            val created =
                NativeGuardedWriteBatch.create(
                    engine = engine,
                    registry = registry,
                    signatureResponse = signatureResponse,
                    signatureLimit = signatureLimit,
                    expectedDraftRevision = catalog.draftRevision,
                )
        ) {
            is GuardedWriteBatchCreateResult.Rejected ->
                LiveTuneRamWriteResult.BatchRejected(
                    reason = created.reason,
                    ramState = readRamStateOrNull(),
                )

            is GuardedWriteBatchCreateResult.Created ->
                created.batch.use { batch ->
                    when (val executed = UsbVerifiedGuardedWriteBatchExecutor(batch).execute(channel, timeoutMs)) {
                        is UsbVerifiedGuardedWriteBatchResult.Reconciled -> {
                            val ramState = engine.readRamSynchronizationState()
                                ?: return LiveTuneRamWriteResult.RamStateUnavailable
                            LiveTuneRamWriteResult.Reconciled(
                                transactionCount = executed.transactionCount,
                                ramState = ramState,
                            )
                        }

                        is UsbVerifiedGuardedWriteBatchResult.Interrupted -> {
                            val ramState = engine.readRamSynchronizationState()
                                ?: return LiveTuneRamWriteResult.RamStateUnavailable
                            LiveTuneRamWriteResult.Interrupted(
                                reconciledTransactionCount = executed.reconciledTransactionCount,
                                cause = executed.cause,
                                ramState = ramState,
                            )
                        }

                        is UsbVerifiedGuardedWriteBatchResult.ReconciliationRejected ->
                            LiveTuneRamWriteResult.ReconciliationRejected(
                                verifiedTransactionCount = executed.verifiedTransactionCount,
                                reason = executed.reason,
                                interruptedCause = executed.interruptedCause,
                                ramState = readRamStateOrNull(),
                            )
                    }
                }
        }
    }

    private fun readRamStateOrNull(): RamSynchronizationState? =
        runCatching { engine.readRamSynchronizationState() }.getOrNull()
}
