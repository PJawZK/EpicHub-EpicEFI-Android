package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel

internal sealed interface UsbVerifiedGuardedWriteBatchResult {
    data class Reconciled(
        val transactionCount: Int,
    ) : UsbVerifiedGuardedWriteBatchResult

    data class Interrupted(
        val reconciledTransactionCount: Int,
        val cause: UsbNativeGuardedWriteBatchResult,
    ) : UsbVerifiedGuardedWriteBatchResult

    data class ReconciliationRejected(
        val verifiedTransactionCount: Int,
        val reason: GuardedWriteBatchReconciliationRejection,
        val interruptedCause: UsbNativeGuardedWriteBatchResult?,
    ) : UsbVerifiedGuardedWriteBatchResult
}

internal class UsbVerifiedGuardedWriteBatchExecutor(
    private val executeTransport: (
        UsbReadOnlyTransactionChannel,
        Int,
    ) -> UsbNativeGuardedWriteBatchResult,
    private val reconcileVerifiedPrefix: (Int) -> GuardedWriteBatchReconciliationResult,
    private val serializeExecution: (((() -> UsbVerifiedGuardedWriteBatchResult)) -> UsbVerifiedGuardedWriteBatchResult) = { block ->
        block()
    },
) {
    constructor(batch: NativeGuardedWriteBatch) :
        this(
            executeTransport = { channel, timeoutMs ->
                UsbNativeGuardedWriteBatchExecutor(batch).execute(channel, timeoutMs)
            },
            reconcileVerifiedPrefix = batch::reconcileVerifiedPrefix,
            serializeExecution = EngineAuthoritySerialization::exclusive,
        )

    fun execute(
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): UsbVerifiedGuardedWriteBatchResult =
        serializeExecution {
            executeSerialized(channel, timeoutMs)
        }

    private fun executeSerialized(
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): UsbVerifiedGuardedWriteBatchResult {
        val transportResult = executeTransport(channel, timeoutMs)
        val verifiedTransactionCount = transportResult.verifiedTransactionPrefix()

        if (verifiedTransactionCount == 0 && transportResult !is UsbNativeGuardedWriteBatchResult.Reconciled) {
            return UsbVerifiedGuardedWriteBatchResult.Interrupted(
                reconciledTransactionCount = 0,
                cause = transportResult,
            )
        }

        return when (val reconciliation = reconcileVerifiedPrefix(verifiedTransactionCount)) {
            GuardedWriteBatchReconciliationResult.Reconciled -> {
                if (transportResult is UsbNativeGuardedWriteBatchResult.Reconciled) {
                    UsbVerifiedGuardedWriteBatchResult.Reconciled(verifiedTransactionCount)
                } else {
                    UsbVerifiedGuardedWriteBatchResult.Interrupted(
                        reconciledTransactionCount = verifiedTransactionCount,
                        cause = transportResult,
                    )
                }
            }
            is GuardedWriteBatchReconciliationResult.Rejected ->
                UsbVerifiedGuardedWriteBatchResult.ReconciliationRejected(
                    verifiedTransactionCount = verifiedTransactionCount,
                    reason = reconciliation.reason,
                    interruptedCause = transportResult.takeUnless {
                        it is UsbNativeGuardedWriteBatchResult.Reconciled
                    },
                )
        }
    }

    private fun UsbNativeGuardedWriteBatchResult.verifiedTransactionPrefix(): Int =
        when (this) {
            is UsbNativeGuardedWriteBatchResult.Reconciled -> transactionCount
            is UsbNativeGuardedWriteBatchResult.AuthorityRejected -> transactionIndex ?: 0
            is UsbNativeGuardedWriteBatchResult.TransportRejected -> transactionIndex
            is UsbNativeGuardedWriteBatchResult.TransportFailure -> transactionIndex
        }.coerceAtLeast(0)
}
