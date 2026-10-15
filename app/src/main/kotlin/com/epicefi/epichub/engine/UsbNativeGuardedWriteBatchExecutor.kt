package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionResult

internal interface GuardedWriteBatchStageAuthority {
    val transactionCount: Int

    fun stagePlan(
        transactionIndex: Int,
        stage: GuardedRamWriteStage,
    ): GuardedWriteBatchStageResult

    fun verifyStage(
        transactionIndex: Int,
        stage: GuardedRamWriteStage,
        responseFrame: ByteArray,
    ): GuardedWriteBatchVerifyResult
}

internal class NativeGuardedWriteBatchStageAuthority(
    private val batch: NativeGuardedWriteBatch,
) : GuardedWriteBatchStageAuthority {
    override val transactionCount: Int
        get() = batch.metadata.transactionCount

    override fun stagePlan(
        transactionIndex: Int,
        stage: GuardedRamWriteStage,
    ): GuardedWriteBatchStageResult = batch.stagePlan(transactionIndex, stage)

    override fun verifyStage(
        transactionIndex: Int,
        stage: GuardedRamWriteStage,
        responseFrame: ByteArray,
    ): GuardedWriteBatchVerifyResult = batch.verifyStage(transactionIndex, stage, responseFrame)
}

internal sealed interface UsbNativeGuardedWriteBatchResult {
    data class Reconciled(
        val transactionCount: Int,
    ) : UsbNativeGuardedWriteBatchResult

    data class AuthorityRejected(
        val stage: GuardedRamWriteStage?,
        val transactionIndex: Int?,
        val reason: GuardedWriteBatchRejection,
    ) : UsbNativeGuardedWriteBatchResult

    data class TransportRejected(
        val stage: GuardedRamWriteStage,
        val transactionIndex: Int,
        val reason: String,
    ) : UsbNativeGuardedWriteBatchResult

    data class TransportFailure(
        val stage: GuardedRamWriteStage,
        val transactionIndex: Int,
        val reason: String,
    ) : UsbNativeGuardedWriteBatchResult
}

internal class UsbNativeGuardedWriteBatchExecutor(
    private val authority: GuardedWriteBatchStageAuthority,
) {
    constructor(batch: NativeGuardedWriteBatch) : this(NativeGuardedWriteBatchStageAuthority(batch))

    fun execute(
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): UsbNativeGuardedWriteBatchResult {
        if (timeoutMs <= 0) {
            return UsbNativeGuardedWriteBatchResult.AuthorityRejected(
                stage = null,
                transactionIndex = null,
                reason = GuardedWriteBatchRejection.InvalidArgument,
            )
        }

        val count = authority.transactionCount
        if (count < 0) {
            return UsbNativeGuardedWriteBatchResult.AuthorityRejected(
                stage = null,
                transactionIndex = null,
                reason = GuardedWriteBatchRejection.PlanFailure,
            )
        }
        if (count == 0) {
            return UsbNativeGuardedWriteBatchResult.Reconciled(0)
        }

        repeat(count) { transactionIndex ->
            val precondition = executeStage(
                channel = channel,
                timeoutMs = timeoutMs,
                transactionIndex = transactionIndex,
                stage = GuardedRamWriteStage.Precondition,
            )
            if (precondition != null) return precondition

            val write = executeStage(
                channel = channel,
                timeoutMs = timeoutMs,
                transactionIndex = transactionIndex,
                stage = GuardedRamWriteStage.Write,
            )
            if (write != null) return write

            val readback = executeStage(
                channel = channel,
                timeoutMs = timeoutMs,
                transactionIndex = transactionIndex,
                stage = GuardedRamWriteStage.Readback,
            )
            if (readback != null) return readback
        }

        return UsbNativeGuardedWriteBatchResult.Reconciled(count)
    }

    private fun executeStage(
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
        transactionIndex: Int,
        stage: GuardedRamWriteStage,
    ): UsbNativeGuardedWriteBatchResult? {
        val plan =
            when (val planned = authority.stagePlan(transactionIndex, stage)) {
                is GuardedWriteBatchStageResult.Planned -> planned.plan
                is GuardedWriteBatchStageResult.Rejected -> {
                    return UsbNativeGuardedWriteBatchResult.AuthorityRejected(
                        stage = stage,
                        transactionIndex = transactionIndex,
                        reason = planned.reason,
                    )
                }
            }

        val response =
            when (
                val transaction = channel.transactExact(
                    request = plan.request,
                    expectedResponseBytes = plan.expectedResponseBytes,
                    timeoutMs = timeoutMs,
                )
            ) {
                is UsbReadOnlyTransactionResult.Success -> transaction.response
                is UsbReadOnlyTransactionResult.Rejected -> {
                    return UsbNativeGuardedWriteBatchResult.TransportRejected(
                        stage = stage,
                        transactionIndex = transactionIndex,
                        reason = transaction.reason,
                    )
                }
                is UsbReadOnlyTransactionResult.TransportFailure -> {
                    return UsbNativeGuardedWriteBatchResult.TransportFailure(
                        stage = stage,
                        transactionIndex = transactionIndex,
                        reason = transaction.reason,
                    )
                }
            }

        return when (val verified = authority.verifyStage(transactionIndex, stage, response)) {
            GuardedWriteBatchVerifyResult.Verified -> null
            is GuardedWriteBatchVerifyResult.Rejected -> {
                UsbNativeGuardedWriteBatchResult.AuthorityRejected(
                    stage = stage,
                    transactionIndex = transactionIndex,
                    reason = verified.reason,
                )
            }
        }
    }
}
