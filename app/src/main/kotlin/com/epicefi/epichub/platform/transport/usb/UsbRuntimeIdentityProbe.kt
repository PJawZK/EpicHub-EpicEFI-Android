package com.epicefi.epichub.platform.transport.usb

import com.epicefi.epichub.engine.NativeRuntimeIdentityValidator
import com.epicefi.epichub.engine.RuntimeIdentityResponseValidator
import com.epicefi.epichub.engine.RuntimeIdentityValidationResult
import com.epicefi.epichub.engine.ValidatedRuntimeControllerIdentity

data class UsbRuntimeIdentityProbePlan(
    val request: ByteArray,
    val maxResponseBytes: Int,
    val timeoutMs: Int,
)

sealed interface UsbRuntimeIdentityProbeResult {
    data class Identity(val identity: ValidatedRuntimeControllerIdentity) : UsbRuntimeIdentityProbeResult

    data class TransportRejected(val result: UsbReadOnlyTransactionResult) : UsbRuntimeIdentityProbeResult

    data class IdentityRejected(val result: RuntimeIdentityValidationResult.Rejected) :
        UsbRuntimeIdentityProbeResult
}

class UsbRuntimeIdentityProbe(
    private val validator: RuntimeIdentityResponseValidator = NativeRuntimeIdentityValidator,
) {
    fun probe(
        channel: UsbReadOnlyTransactionChannel,
        plan: UsbRuntimeIdentityProbePlan,
    ): UsbRuntimeIdentityProbeResult {
        val transaction =
            channel.transact(
                request = plan.request,
                maxResponseBytes = plan.maxResponseBytes,
                timeoutMs = plan.timeoutMs,
            )
        val response =
            when (transaction) {
                is UsbReadOnlyTransactionResult.Success -> transaction.response
                else -> return UsbRuntimeIdentityProbeResult.TransportRejected(transaction)
            }

        return when (val validation = validator.validate(response, plan.maxResponseBytes)) {
            is RuntimeIdentityValidationResult.Valid ->
                UsbRuntimeIdentityProbeResult.Identity(validation.identity)
            is RuntimeIdentityValidationResult.Rejected ->
                UsbRuntimeIdentityProbeResult.IdentityRejected(validation)
        }
    }
}
