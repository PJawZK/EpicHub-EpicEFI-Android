package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionResult
import java.io.ByteArrayOutputStream

sealed interface UsbReadOnlyPageAcquisitionResult {
    data class Acquired(
        val page: ByteArray,
    ) : UsbReadOnlyPageAcquisitionResult

    data class AuthorityRejected(
        val reason: ReadOnlyPageAuthorityRejection,
    ) : UsbReadOnlyPageAcquisitionResult

    data class TransportRejected(
        val reason: String,
    ) : UsbReadOnlyPageAcquisitionResult

    data class TransportFailure(
        val reason: String,
    ) : UsbReadOnlyPageAcquisitionResult
}

class UsbReadOnlyPageAcquirer internal constructor(
    private val authority: ReadOnlyPageAuthority,
) {
    constructor(registry: NativeProfileRegistry) : this(
        NativeReadOnlyPageAuthority(handleProvider = registry::readOnlyRegistryHandle),
    )

    fun acquirePage(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        pageIndex: Int,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): UsbReadOnlyPageAcquisitionResult {
        if (timeoutMs <= 0) {
            return UsbReadOnlyPageAcquisitionResult.TransportRejected("timeout must be positive")
        }

        val planned = authority.planPage(signatureResponse, signatureLimit, pageIndex)
        val transfers =
            when (planned) {
                is ReadOnlyPagePlanResult.Planned -> planned.transfers
                is ReadOnlyPagePlanResult.Rejected -> {
                    return UsbReadOnlyPageAcquisitionResult.AuthorityRejected(planned.reason)
                }
            }

        val page = ByteArrayOutputStream()
        transfers.forEachIndexed { planIndex, transfer ->
            when (
                val transaction =
                    channel.transactExact(
                        request = transfer.request,
                        expectedResponseBytes = transfer.expectedResponseBytes,
                        timeoutMs = timeoutMs,
                    )
            ) {
                is UsbReadOnlyTransactionResult.Success -> {
                    when (
                        val verified =
                            authority.verifyChunk(
                                signatureResponse = signatureResponse,
                                signatureLimit = signatureLimit,
                                pageIndex = pageIndex,
                                planIndex = planIndex,
                                responseFrame = transaction.response,
                            )
                    ) {
                        is ReadOnlyPageVerifyResult.Verified -> page.write(verified.payload)
                        is ReadOnlyPageVerifyResult.Rejected -> {
                            return UsbReadOnlyPageAcquisitionResult.AuthorityRejected(
                                verified.reason,
                            )
                        }
                    }
                }

                is UsbReadOnlyTransactionResult.Rejected -> {
                    return UsbReadOnlyPageAcquisitionResult.TransportRejected(transaction.reason)
                }

                is UsbReadOnlyTransactionResult.TransportFailure -> {
                    return UsbReadOnlyPageAcquisitionResult.TransportFailure(transaction.reason)
                }
            }
        }

        return UsbReadOnlyPageAcquisitionResult.Acquired(page.toByteArray())
    }
}
