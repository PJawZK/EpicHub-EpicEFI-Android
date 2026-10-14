package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel

sealed interface UsbReadOnlyTuneLoadResult {
    data class Loaded(
        val profileGeneration: Long,
        val pageCount: Int,
    ) : UsbReadOnlyTuneLoadResult

    data class AuthorityRejected(
        val reason: ReadOnlyPageAuthorityRejection,
    ) : UsbReadOnlyTuneLoadResult

    data class TransportRejected(
        val pageIndex: Int,
        val reason: String,
    ) : UsbReadOnlyTuneLoadResult

    data class TransportFailure(
        val pageIndex: Int,
        val reason: String,
    ) : UsbReadOnlyTuneLoadResult

    data class InstallRejected(
        val reason: LiveTuneInstallRejection,
    ) : UsbReadOnlyTuneLoadResult
}

internal fun interface ReadOnlyPageAcquisitionOperation {
    fun acquire(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        pageIndex: Int,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): UsbReadOnlyPageAcquisitionResult
}

internal fun interface LiveTuneInstallOperation {
    fun install(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        pages: List<ByteArray>,
    ): LiveTuneInstallResult
}

class UsbReadOnlyTuneLoader internal constructor(
    private val metadataAuthority: ReadOnlyTuneMetadataAuthority,
    private val pageAcquisition: ReadOnlyPageAcquisitionOperation,
    private val liveInstall: LiveTuneInstallOperation,
) {
    constructor(engine: NativeEngine, registry: NativeProfileRegistry) : this(
        metadataAuthority =
            NativeReadOnlyTuneMetadataAuthority(
                handleProvider = registry::readOnlyRegistryHandle,
            ),
        pageAcquisition =
            UsbReadOnlyPageAcquirer(registry).let { acquirer ->
                ReadOnlyPageAcquisitionOperation(acquirer::acquirePage)
            },
        liveInstall =
            NativeLiveTuneInstaller(engine, registry).let { installer ->
                LiveTuneInstallOperation(installer::install)
            },
    )

    fun load(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): UsbReadOnlyTuneLoadResult {
        if (timeoutMs <= 0) {
            return UsbReadOnlyTuneLoadResult.TransportRejected(
                pageIndex = -1,
                reason = "timeout must be positive",
            )
        }

        val pageCount =
            when (val metadata = metadataAuthority.pageCount(signatureResponse, signatureLimit)) {
                is ReadOnlyTunePageCountResult.Available -> metadata.pageCount
                is ReadOnlyTunePageCountResult.Rejected -> {
                    return UsbReadOnlyTuneLoadResult.AuthorityRejected(metadata.reason)
                }
            }

        val pages = ArrayList<ByteArray>(pageCount)
        repeat(pageCount) { pageIndex ->
            when (
                val acquired =
                    pageAcquisition.acquire(
                        signatureResponse,
                        signatureLimit,
                        pageIndex,
                        channel,
                        timeoutMs,
                    )
            ) {
                is UsbReadOnlyPageAcquisitionResult.Acquired -> pages += acquired.page
                is UsbReadOnlyPageAcquisitionResult.AuthorityRejected -> {
                    return UsbReadOnlyTuneLoadResult.AuthorityRejected(acquired.reason)
                }
                is UsbReadOnlyPageAcquisitionResult.TransportRejected -> {
                    return UsbReadOnlyTuneLoadResult.TransportRejected(pageIndex, acquired.reason)
                }
                is UsbReadOnlyPageAcquisitionResult.TransportFailure -> {
                    return UsbReadOnlyTuneLoadResult.TransportFailure(pageIndex, acquired.reason)
                }
            }
        }

        return when (val installed = liveInstall.install(signatureResponse, signatureLimit, pages)) {
            is LiveTuneInstallResult.Installed ->
                UsbReadOnlyTuneLoadResult.Loaded(installed.profileGeneration, pageCount)
            is LiveTuneInstallResult.Rejected ->
                UsbReadOnlyTuneLoadResult.InstallRejected(installed.reason)
        }
    }
}
