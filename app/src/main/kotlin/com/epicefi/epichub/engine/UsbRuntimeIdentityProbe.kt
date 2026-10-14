package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionResult

data class RuntimeIdentityProbePlan(
    val query: ByteArray,
    val maxResponseBytes: Int,
    val timeoutMs: Int,
)

sealed interface UsbRuntimeIdentityProbeResult {
    data class Identified(
        val identity: ValidatedRuntimeIdentity,
    ) : UsbRuntimeIdentityProbeResult

    data class TransportRejected(
        val reason: String,
    ) : UsbRuntimeIdentityProbeResult

    data class TransportFailure(
        val reason: String,
    ) : UsbRuntimeIdentityProbeResult

    data class IdentityRejected(
        val reason: RuntimeIdentityRejection,
    ) : UsbRuntimeIdentityProbeResult
}

class UsbRuntimeIdentityProbe(
    private val authority: RuntimeIdentityResponseAuthority = NativeRuntimeIdentityAuthority(),
) {
    fun probe(
        channel: UsbReadOnlyTransactionChannel,
        plan: RuntimeIdentityProbePlan,
    ): UsbRuntimeIdentityProbeResult =
        when (
            val transaction =
                channel.transact(
                    request = plan.query,
                    maxResponseBytes = plan.maxResponseBytes,
                    timeoutMs = plan.timeoutMs,
                )
        ) {
            is UsbReadOnlyTransactionResult.Rejected ->
                UsbRuntimeIdentityProbeResult.TransportRejected(transaction.reason)
            is UsbReadOnlyTransactionResult.TransportFailure ->
                UsbRuntimeIdentityProbeResult.TransportFailure(transaction.reason)
            is UsbReadOnlyTransactionResult.Success ->
                when (
                    val validation =
                        authority.validate(
                            response = transaction.response,
                            maxResponseBytes = plan.maxResponseBytes,
                        )
                ) {
                    is RuntimeIdentityValidationResult.Accepted ->
                        UsbRuntimeIdentityProbeResult.Identified(validation.identity)
                    is RuntimeIdentityValidationResult.Rejected ->
                        UsbRuntimeIdentityProbeResult.IdentityRejected(validation.reason)
                }
        }
}
