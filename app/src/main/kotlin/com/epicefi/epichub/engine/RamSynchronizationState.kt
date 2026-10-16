package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val RAM_STATE_SNAPSHOT_SIZE = 64
private const val RAM_STATE_SNAPSHOT_VERSION = 1
private const val RAM_STATE_STATUS_OK = 0
private const val RAM_STATE_STATUS_INVALID_HANDLE = 1
private const val RAM_STATE_STATUS_NOT_DIRECT = 2
private const val RAM_STATE_STATUS_TOO_SMALL = 3
private const val RAM_STATE_STATUS_UNAVAILABLE = 4
private const val RAM_STATE_STATUS_PANIC = 255
private const val FLAG_CONFIRMED_REVISION = 0x01
private const val FLAG_LAST_RECONCILIATION = 0x02
private const val FLAG_LAST_RECONCILIATION_COMPLETE = 0x04

internal val RAM_STATE_MAGIC = byteArrayOf('R'.code.toByte(), 'A'.code.toByte(), 'M'.code.toByte(), '1'.code.toByte())

enum class RamSynchronizationStatus {
    FreshLiveControllerConfirmation,
    CurrentDraftConfirmedInRam,
    LocalDraftAheadOfConfirmedRam,
    PartiallyVerifiedInterruptedWrite,
    RamUnconfirmedAfterInterruptedWrite,
}

enum class RamSourceAcquisition {
    LiveControllerRead,
}

data class RamWriteReconciliationEvidence(
    val draftRevision: Long,
    val verifiedTransactionCount: Long,
    val transactionCount: Long,
    val complete: Boolean,
)

data class RamSynchronizationState(
    val status: RamSynchronizationStatus,
    val profileGeneration: Long,
    val acquisition: RamSourceAcquisition,
    val currentDraftRevision: Long,
    val confirmedDraftRevision: Long?,
    val verifiedWriteRangeCount: Long,
    val lastReconciliation: RamWriteReconciliationEvidence?,
)

internal class NativeRamSynchronizationStateBuffer {
    private val buffer = ByteBuffer.allocateDirect(RAM_STATE_SNAPSHOT_SIZE).order(ByteOrder.LITTLE_ENDIAN)

    fun read(handle: Long): RamSynchronizationState? {
        buffer.clear()
        return when (val status = NativeEngineBridge.fillRamSourceState(handle, buffer)) {
            RAM_STATE_STATUS_OK -> decodeRamSynchronizationStateSnapshot(buffer)
            RAM_STATE_STATUS_UNAVAILABLE -> null
            RAM_STATE_STATUS_INVALID_HANDLE -> error("Native RAM synchronization state rejected an invalid engine handle.")
            RAM_STATE_STATUS_NOT_DIRECT -> error("Native RAM synchronization state requires a direct buffer.")
            RAM_STATE_STATUS_TOO_SMALL -> error("Native RAM synchronization state buffer is too small.")
            RAM_STATE_STATUS_PANIC -> error("Native RAM synchronization state failed inside the Rust boundary.")
            else -> error("Native RAM synchronization state failed with status $status.")
        }
    }
}

internal fun decodeRamSynchronizationStateSnapshot(source: ByteBuffer): RamSynchronizationState {
    val buffer = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    buffer.position(0)
    require(buffer.capacity() >= RAM_STATE_SNAPSHOT_SIZE) { "RAM synchronization snapshot is too small." }

    val magic = ByteArray(4)
    buffer.get(magic)
    require(magic.contentEquals(RAM_STATE_MAGIC)) { "RAM synchronization snapshot magic is invalid." }

    val status =
        when (buffer.get().toInt() and 0xFF) {
            1 -> RamSynchronizationStatus.FreshLiveControllerConfirmation
            2 -> RamSynchronizationStatus.CurrentDraftConfirmedInRam
            3 -> RamSynchronizationStatus.LocalDraftAheadOfConfirmedRam
            4 -> RamSynchronizationStatus.PartiallyVerifiedInterruptedWrite
            5 -> RamSynchronizationStatus.RamUnconfirmedAfterInterruptedWrite
            else -> error("RAM synchronization snapshot status is unknown.")
        }
    val acquisition =
        when (buffer.get().toInt() and 0xFF) {
            1 -> RamSourceAcquisition.LiveControllerRead
            else -> error("RAM synchronization snapshot acquisition is unknown.")
        }
    val flags = buffer.get().toInt() and 0xFF
    val version = buffer.get().toInt() and 0xFF
    require(version == RAM_STATE_SNAPSHOT_VERSION) { "RAM synchronization snapshot version $version is unsupported." }

    val profileGeneration = buffer.nonNegativeLong("profile generation")
    val currentDraftRevision = buffer.nonNegativeLong("current draft revision")
    val confirmedRaw = buffer.long
    val confirmedDraftRevision =
        if (flags and FLAG_CONFIRMED_REVISION != 0) {
            require(confirmedRaw >= 0) { "Confirmed RAM draft revision is invalid." }
            confirmedRaw
        } else {
            require(confirmedRaw == -1L) { "Unconfirmed RAM state carried a draft revision." }
            null
        }
    val verifiedWriteRangeCount = buffer.nonNegativeLong("verified write range count")
    val lastDraftRaw = buffer.long
    val lastVerifiedRaw = buffer.long
    val lastTransactionRaw = buffer.long
    val hasLastReconciliation = flags and FLAG_LAST_RECONCILIATION != 0
    val complete = flags and FLAG_LAST_RECONCILIATION_COMPLETE != 0
    val lastReconciliation =
        if (hasLastReconciliation) {
            require(lastDraftRaw >= 0) { "Last RAM reconciliation draft revision is invalid." }
            require(lastVerifiedRaw >= 0) { "Last verified transaction count is invalid." }
            require(lastTransactionRaw >= 0) { "Last transaction count is invalid." }
            require(lastVerifiedRaw <= lastTransactionRaw) {
                "Verified transaction count exceeds the reconciliation transaction count."
            }
            require(!complete || lastVerifiedRaw == lastTransactionRaw) {
                "Complete RAM reconciliation does not cover the full transaction batch."
            }
            RamWriteReconciliationEvidence(
                draftRevision = lastDraftRaw,
                verifiedTransactionCount = lastVerifiedRaw,
                transactionCount = lastTransactionRaw,
                complete = complete,
            )
        } else {
            require(!complete) { "RAM snapshot marks a missing reconciliation complete." }
            require(lastDraftRaw == -1L && lastVerifiedRaw == 0L && lastTransactionRaw == 0L) {
                "RAM snapshot carries reconciliation evidence without the reconciliation flag."
            }
            null
        }

    validateSemanticState(
        status = status,
        currentDraftRevision = currentDraftRevision,
        confirmedDraftRevision = confirmedDraftRevision,
        lastReconciliation = lastReconciliation,
    )

    return RamSynchronizationState(
        status = status,
        profileGeneration = profileGeneration,
        acquisition = acquisition,
        currentDraftRevision = currentDraftRevision,
        confirmedDraftRevision = confirmedDraftRevision,
        verifiedWriteRangeCount = verifiedWriteRangeCount,
        lastReconciliation = lastReconciliation,
    )
}

private fun ByteBuffer.nonNegativeLong(label: String): Long =
    long.also { value -> require(value >= 0) { "RAM synchronization snapshot $label is invalid." } }

private fun validateSemanticState(
    status: RamSynchronizationStatus,
    currentDraftRevision: Long,
    confirmedDraftRevision: Long?,
    lastReconciliation: RamWriteReconciliationEvidence?,
) {
    when (status) {
        RamSynchronizationStatus.FreshLiveControllerConfirmation -> {
            require(confirmedDraftRevision == currentDraftRevision) {
                "Fresh live RAM confirmation does not match the current draft revision."
            }
            require(lastReconciliation == null) {
                "Fresh live RAM confirmation unexpectedly carries write reconciliation evidence."
            }
        }
        RamSynchronizationStatus.CurrentDraftConfirmedInRam -> {
            require(confirmedDraftRevision == currentDraftRevision) {
                "Confirmed RAM revision does not match the current draft revision."
            }
            require(lastReconciliation?.complete == true) {
                "Current-draft RAM confirmation requires complete write reconciliation evidence."
            }
        }
        RamSynchronizationStatus.LocalDraftAheadOfConfirmedRam -> {
            require(confirmedDraftRevision != null && confirmedDraftRevision != currentDraftRevision) {
                "Local-draft-ahead state requires a different confirmed RAM revision."
            }
            require(lastReconciliation?.complete != false) {
                "Local-draft-ahead state cannot represent an interrupted reconciliation."
            }
        }
        RamSynchronizationStatus.PartiallyVerifiedInterruptedWrite -> {
            require(confirmedDraftRevision == null) {
                "Partial RAM verification cannot claim a whole-draft confirmation."
            }
            require(lastReconciliation != null && !lastReconciliation.complete) {
                "Partial RAM verification requires incomplete reconciliation evidence."
            }
            require(lastReconciliation.verifiedTransactionCount > 0) {
                "Partial RAM verification requires at least one verified transaction."
            }
        }
        RamSynchronizationStatus.RamUnconfirmedAfterInterruptedWrite -> {
            require(confirmedDraftRevision == null) {
                "Unconfirmed RAM state cannot claim a whole-draft confirmation."
            }
            require(lastReconciliation != null && !lastReconciliation.complete) {
                "Unconfirmed interrupted RAM state requires incomplete reconciliation evidence."
            }
            require(lastReconciliation.verifiedTransactionCount == 0L) {
                "Zero-prefix RAM uncertainty cannot claim verified transactions."
            }
        }
    }
}
