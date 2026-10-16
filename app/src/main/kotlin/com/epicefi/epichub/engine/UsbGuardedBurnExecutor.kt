package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionResult

internal interface GuardedBurnStageAuthority {
    fun stagePlan(stage: GuardedBurnStage): GuardedBurnStageResult

    fun verifyStage(
        stage: GuardedBurnStage,
        responseFrame: ByteArray,
    ): GuardedBurnVerifyResult
}

internal class NativeGuardedBurnStageAuthority(
    private val plan: NativeGuardedBurn,
) : GuardedBurnStageAuthority {
    override fun stagePlan(stage: GuardedBurnStage): GuardedBurnStageResult = plan.stagePlan(stage)

    override fun verifyStage(
        stage: GuardedBurnStage,
        responseFrame: ByteArray,
    ): GuardedBurnVerifyResult = plan.verifyStage(stage, responseFrame)
}

internal sealed interface UsbGuardedBurnResult {
    data object PersistedAndVerified : UsbGuardedBurnResult

    data class AuthorityRejected(
        val stage: GuardedBurnStage?,
        val reason: GuardedBurnRejection,
    ) : UsbGuardedBurnResult

    data class TransportRejected(
        val stage: GuardedBurnStage,
        val reason: String,
    ) : UsbGuardedBurnResult

    data class TransportFailure(
        val stage: GuardedBurnStage,
        val reason: String,
    ) : UsbGuardedBurnResult
}

internal class UsbGuardedBurnExecutor(
    private val authority: GuardedBurnStageAuthority,
    private val serializeExecution: ((() -> UsbGuardedBurnResult) -> UsbGuardedBurnResult) =
        { block -> block() },
) {
    constructor(plan: NativeGuardedBurn) :
        this(
            authority = NativeGuardedBurnStageAuthority(plan),
            serializeExecution = EngineAuthoritySerialization::exclusive,
        )

    fun execute(
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): UsbGuardedBurnResult =
        serializeExecution {
            executeSerialized(channel, timeoutMs)
        }

    private fun executeSerialized(
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): UsbGuardedBurnResult {
        if (timeoutMs <= 0) {
            return UsbGuardedBurnResult.AuthorityRejected(
                stage = null,
                reason = GuardedBurnRejection.InvalidArgument,
            )
        }

        executeStage(channel, timeoutMs, GuardedBurnStage.Burn)?.let { return it }
        executeStage(channel, timeoutMs, GuardedBurnStage.Crc)?.let { return it }
        return UsbGuardedBurnResult.PersistedAndVerified
    }

    private fun executeStage(
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
        stage: GuardedBurnStage,
    ): UsbGuardedBurnResult? {
        val plan =
            when (val planned = authority.stagePlan(stage)) {
                is GuardedBurnStageResult.Planned -> planned.plan
                is GuardedBurnStageResult.Rejected -> {
                    return UsbGuardedBurnResult.AuthorityRejected(
                        stage = stage,
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
                    return UsbGuardedBurnResult.TransportRejected(
                        stage = stage,
                        reason = transaction.reason,
                    )
                }
                is UsbReadOnlyTransactionResult.TransportFailure -> {
                    return UsbGuardedBurnResult.TransportFailure(
                        stage = stage,
                        reason = transaction.reason,
                    )
                }
            }

        return when (val verified = authority.verifyStage(stage, response)) {
            GuardedBurnVerifyResult.Verified -> null
            is GuardedBurnVerifyResult.Rejected -> {
                UsbGuardedBurnResult.AuthorityRejected(
                    stage = stage,
                    reason = verified.reason,
                )
            }
        }
    }
}
