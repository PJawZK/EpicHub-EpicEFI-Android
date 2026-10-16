package com.epicefi.epichub.ui.tuner

import com.epicefi.epichub.engine.GuardedWriteBatchReconciliationRejection
import com.epicefi.epichub.engine.LiveTuneOwnerRamWriteResult
import com.epicefi.epichub.engine.LiveTuneRamWriteResult
import com.epicefi.epichub.engine.RamSynchronizationState
import com.epicefi.epichub.engine.RamSynchronizationStatus
import com.epicefi.epichub.engine.UsbNativeGuardedWriteBatchResult
import com.epicefi.epichub.platform.transport.usb.AndroidUsbLiveRamWriteResult

data class RamWriteControlState(
    val synchronization: RamSynchronizationState? = null,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val actionEnabled: Boolean
        get() =
            !busy &&
                when (synchronization?.status) {
                    RamSynchronizationStatus.LocalDraftAheadOfConfirmedRam,
                    RamSynchronizationStatus.PartiallyVerifiedInterruptedWrite,
                    RamSynchronizationStatus.RamUnconfirmedAfterInterruptedWrite,
                    -> true

                    RamSynchronizationStatus.FreshLiveControllerConfirmation,
                    RamSynchronizationStatus.CurrentDraftConfirmedInRam,
                    null,
                    -> false
                }

    val actionLabel: String
        get() =
            when {
                busy -> "Writing RAM…"
                synchronization?.status == RamSynchronizationStatus.PartiallyVerifiedInterruptedWrite ||
                    synchronization?.status == RamSynchronizationStatus.RamUnconfirmedAfterInterruptedWrite ->
                    "Retry write to RAM"
                actionEnabled -> "Write draft to RAM"
                synchronization != null -> "RAM current"
                else -> "Write to RAM"
            }

    fun refresh(state: RamSynchronizationState?): RamWriteControlState =
        copy(synchronization = state)

    fun started(): RamWriteControlState =
        copy(busy = true, message = "Writing current draft to volatile ECU RAM and verifying exact readback…")

    internal fun completed(result: AndroidUsbLiveRamWriteResult): RamWriteControlState =
        when (result) {
            is AndroidUsbLiveRamWriteResult.Completed -> completed(result.result)
            AndroidUsbLiveRamWriteResult.NotConnected ->
                copy(
                    synchronization = null,
                    busy = false,
                    message = "RAM write unavailable: controller is not connected.",
                )
            AndroidUsbLiveRamWriteResult.OwnerClosed ->
                copy(
                    synchronization = null,
                    busy = false,
                    message = "RAM write unavailable: USB connection owner is closed.",
                )
        }

    fun disconnected(): RamWriteControlState =
        RamWriteControlState(
            message = "No live ECU RAM authority. Local draft changes remain local.",
        )

    private fun completed(result: LiveTuneOwnerRamWriteResult): RamWriteControlState =
        when (result) {
            is LiveTuneOwnerRamWriteResult.Completed -> completed(result.result)
            is LiveTuneOwnerRamWriteResult.NotWritable ->
                copy(
                    synchronization = null,
                    busy = false,
                    message = "RAM write unavailable until the exact live controller tune is loaded (${result.state}).",
                )
            LiveTuneOwnerRamWriteResult.OperationUnavailable ->
                copy(
                    busy = false,
                    message = "RAM write authority is unavailable for the selected live profile.",
                )
            LiveTuneOwnerRamWriteResult.OwnerClosed ->
                copy(
                    synchronization = null,
                    busy = false,
                    message = "RAM write unavailable: live tune authority is closed.",
                )
        }

    private fun completed(result: LiveTuneRamWriteResult): RamWriteControlState =
        when (result) {
            is LiveTuneRamWriteResult.Reconciled ->
                RamWriteControlState(
                    synchronization = result.ramState,
                    message =
                        if (result.transactionCount == 0) {
                            "No RAM byte changes were required; the current draft is verified against ECU RAM."
                        } else {
                            "Verified ${result.transactionCount} RAM transaction(s) by exact readback. Changes are volatile and not Burned."
                        },
                )

            is LiveTuneRamWriteResult.Interrupted ->
                RamWriteControlState(
                    synchronization = result.ramState,
                    message =
                        "RAM write interrupted after ${result.reconciledTransactionCount} verified transaction(s): " +
                            result.cause.describeFailure(),
                )

            is LiveTuneRamWriteResult.ReconciliationRejected ->
                RamWriteControlState(
                    synchronization = result.ramState,
                    message =
                        buildString {
                            append("RAM reconciliation rejected after ${result.verifiedTransactionCount} verified transaction(s): ")
                            append(result.reason.describe())
                            result.interruptedCause?.let { cause ->
                                append("; transfer interruption: ")
                                append(cause.describeFailure())
                            }
                        },
                )

            is LiveTuneRamWriteResult.BatchRejected ->
                RamWriteControlState(
                    synchronization = result.ramState,
                    message = "RAM write authorization rejected: ${result.reason}.",
                )

            LiveTuneRamWriteResult.RamStateUnavailable ->
                RamWriteControlState(
                    synchronization = null,
                    message = "RAM transfer finished, but authoritative post-write RAM state is unavailable. No synchronization claim is made.",
                )
        }

    companion object {
        fun fromSynchronization(state: RamSynchronizationState?): RamWriteControlState =
            RamWriteControlState(synchronization = state)
    }
}

private fun GuardedWriteBatchReconciliationRejection.describe(): String =
    name.lowercase().replace('_', ' ')

private fun UsbNativeGuardedWriteBatchResult.describeFailure(): String =
    when (this) {
        is UsbNativeGuardedWriteBatchResult.AuthorityRejected ->
            "authority rejected ${stage?.name ?: "batch"}" +
                (transactionIndex?.let { " at transaction $it" } ?: "") +
                ": $reason"
        is UsbNativeGuardedWriteBatchResult.TransportRejected ->
            "transport rejected ${stage.name} at transaction $transactionIndex: $reason"
        is UsbNativeGuardedWriteBatchResult.TransportFailure ->
            "transport failed ${stage.name} at transaction $transactionIndex: $reason"
        is UsbNativeGuardedWriteBatchResult.Reconciled ->
            "unexpected reconciled transport result (${transactionCount} transaction(s))"
    }
