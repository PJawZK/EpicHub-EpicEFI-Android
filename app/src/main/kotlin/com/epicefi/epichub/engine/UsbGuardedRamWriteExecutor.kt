package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionResult

internal data class GuardedRamWriteIntent(
    val page: Int,
    val offset: Int,
    val before: ByteArray,
    val after: ByteArray,
)

internal data class GuardedRamWriteStagePlan(
    val request: ByteArray,
    val expectedResponseBytes: Int,
)

internal data class GuardedRamWriteTransferPlan(
    val precondition: GuardedRamWriteStagePlan,
    val write: GuardedRamWriteStagePlan,
    val readback: GuardedRamWriteStagePlan,
)

internal enum class GuardedRamWriteAuthorityRejection {
    InvalidArgument,
    InvalidHandle,
    UnknownController,
    RecognizedControllerWithoutProfile,
    MissingCompiledBundle,
    PlanFailure,
    EnvelopeFailure,
    StalePrecondition,
    WriteAcknowledgementFailure,
    ReadbackMismatch,
    RegistryFailure,
    NativeFailure,
}

internal sealed interface GuardedRamWritePlanResult {
    data class Planned(
        val transfers: List<GuardedRamWriteTransferPlan>,
    ) : GuardedRamWritePlanResult

    data class Rejected(
        val reason: GuardedRamWriteAuthorityRejection,
    ) : GuardedRamWritePlanResult
}

internal sealed interface GuardedRamWriteVerifyResult {
    data object Verified : GuardedRamWriteVerifyResult

    data class Rejected(
        val reason: GuardedRamWriteAuthorityRejection,
    ) : GuardedRamWriteVerifyResult
}

internal interface GuardedRamWriteAuthority {
    fun plan(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        intent: GuardedRamWriteIntent,
    ): GuardedRamWritePlanResult

    fun verifyPrecondition(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        intent: GuardedRamWriteIntent,
        planIndex: Int,
        responseFrame: ByteArray,
    ): GuardedRamWriteVerifyResult

    fun verifyWriteAcknowledgement(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        intent: GuardedRamWriteIntent,
        planIndex: Int,
        responseFrame: ByteArray,
    ): GuardedRamWriteVerifyResult

    fun verifyReadback(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        intent: GuardedRamWriteIntent,
        planIndex: Int,
        responseFrame: ByteArray,
    ): GuardedRamWriteVerifyResult
}

internal enum class GuardedRamWriteStage {
    Precondition,
    Write,
    Readback,
}

internal sealed interface UsbGuardedRamWriteResult {
    data class Reconciled(
        val transferCount: Int,
    ) : UsbGuardedRamWriteResult

    data class AuthorityRejected(
        val stage: GuardedRamWriteStage?,
        val planIndex: Int?,
        val reason: GuardedRamWriteAuthorityRejection,
    ) : UsbGuardedRamWriteResult

    data class TransportRejected(
        val stage: GuardedRamWriteStage,
        val planIndex: Int,
        val reason: String,
    ) : UsbGuardedRamWriteResult

    data class TransportFailure(
        val stage: GuardedRamWriteStage,
        val planIndex: Int,
        val reason: String,
    ) : UsbGuardedRamWriteResult
}

internal class UsbGuardedRamWriteExecutor(
    private val authority: GuardedRamWriteAuthority,
) {
    fun execute(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        intent: GuardedRamWriteIntent,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): UsbGuardedRamWriteResult {
        if (timeoutMs <= 0 || signatureLimit <= 0) {
            return UsbGuardedRamWriteResult.AuthorityRejected(
                stage = null,
                planIndex = null,
                reason = GuardedRamWriteAuthorityRejection.InvalidArgument,
            )
        }

        val transfers =
            when (val planned = authority.plan(signatureResponse, signatureLimit, intent)) {
                is GuardedRamWritePlanResult.Planned -> planned.transfers
                is GuardedRamWritePlanResult.Rejected -> {
                    return UsbGuardedRamWriteResult.AuthorityRejected(
                        stage = null,
                        planIndex = null,
                        reason = planned.reason,
                    )
                }
            }
        if (transfers.isEmpty()) {
            return UsbGuardedRamWriteResult.AuthorityRejected(
                stage = null,
                planIndex = null,
                reason = GuardedRamWriteAuthorityRejection.PlanFailure,
            )
        }

        transfers.forEachIndexed { planIndex, transfer ->
            val precondition =
                transact(
                    channel = channel,
                    plan = transfer.precondition,
                    timeoutMs = timeoutMs,
                    stage = GuardedRamWriteStage.Precondition,
                    planIndex = planIndex,
                )
            val preconditionResponse =
                when (precondition) {
                    is StageTransaction.Success -> precondition.response
                    is StageTransaction.Failed -> return precondition.result
                }
            when (
                val verified =
                    authority.verifyPrecondition(
                        signatureResponse,
                        signatureLimit,
                        intent,
                        planIndex,
                        preconditionResponse,
                    )
            ) {
                GuardedRamWriteVerifyResult.Verified -> Unit
                is GuardedRamWriteVerifyResult.Rejected -> {
                    return UsbGuardedRamWriteResult.AuthorityRejected(
                        stage = GuardedRamWriteStage.Precondition,
                        planIndex = planIndex,
                        reason = verified.reason,
                    )
                }
            }

            val write =
                transact(
                    channel = channel,
                    plan = transfer.write,
                    timeoutMs = timeoutMs,
                    stage = GuardedRamWriteStage.Write,
                    planIndex = planIndex,
                )
            val writeResponse =
                when (write) {
                    is StageTransaction.Success -> write.response
                    is StageTransaction.Failed -> return write.result
                }
            when (
                val verified =
                    authority.verifyWriteAcknowledgement(
                        signatureResponse,
                        signatureLimit,
                        intent,
                        planIndex,
                        writeResponse,
                    )
            ) {
                GuardedRamWriteVerifyResult.Verified -> Unit
                is GuardedRamWriteVerifyResult.Rejected -> {
                    return UsbGuardedRamWriteResult.AuthorityRejected(
                        stage = GuardedRamWriteStage.Write,
                        planIndex = planIndex,
                        reason = verified.reason,
                    )
                }
            }

            val readback =
                transact(
                    channel = channel,
                    plan = transfer.readback,
                    timeoutMs = timeoutMs,
                    stage = GuardedRamWriteStage.Readback,
                    planIndex = planIndex,
                )
            val readbackResponse =
                when (readback) {
                    is StageTransaction.Success -> readback.response
                    is StageTransaction.Failed -> return readback.result
                }
            when (
                val verified =
                    authority.verifyReadback(
                        signatureResponse,
                        signatureLimit,
                        intent,
                        planIndex,
                        readbackResponse,
                    )
            ) {
                GuardedRamWriteVerifyResult.Verified -> Unit
                is GuardedRamWriteVerifyResult.Rejected -> {
                    return UsbGuardedRamWriteResult.AuthorityRejected(
                        stage = GuardedRamWriteStage.Readback,
                        planIndex = planIndex,
                        reason = verified.reason,
                    )
                }
            }
        }

        return UsbGuardedRamWriteResult.Reconciled(transfers.size)
    }

    private fun transact(
        channel: UsbReadOnlyTransactionChannel,
        plan: GuardedRamWriteStagePlan,
        timeoutMs: Int,
        stage: GuardedRamWriteStage,
        planIndex: Int,
    ): StageTransaction =
        when (
            val transaction =
                channel.transactExact(
                    request = plan.request,
                    expectedResponseBytes = plan.expectedResponseBytes,
                    timeoutMs = timeoutMs,
                )
        ) {
            is UsbReadOnlyTransactionResult.Success -> StageTransaction.Success(transaction.response)
            is UsbReadOnlyTransactionResult.Rejected ->
                StageTransaction.Failed(
                    UsbGuardedRamWriteResult.TransportRejected(stage, planIndex, transaction.reason),
                )
            is UsbReadOnlyTransactionResult.TransportFailure ->
                StageTransaction.Failed(
                    UsbGuardedRamWriteResult.TransportFailure(stage, planIndex, transaction.reason),
                )
        }

    private sealed interface StageTransaction {
        data class Success(val response: ByteArray) : StageTransaction

        data class Failed(val result: UsbGuardedRamWriteResult) : StageTransaction
    }
}
