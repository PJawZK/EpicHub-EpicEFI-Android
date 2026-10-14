package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyBulkPort
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class UsbReadOnlyTuneLoaderTest {
    @Test
    fun authoritative_page_count_drives_all_pages_then_single_install() {
        val requestedPages = mutableListOf<Int>()
        var installCalls = 0
        var installedPages = emptyList<ByteArray>()
        val loader =
            UsbReadOnlyTuneLoader(
                metadataAuthority = ReadOnlyTuneMetadataAuthority { _, _ ->
                    ReadOnlyTunePageCountResult.Available(2)
                },
                pageAcquisition =
                    ReadOnlyPageAcquisitionOperation { _, _, pageIndex, _, _ ->
                        requestedPages += pageIndex
                        UsbReadOnlyPageAcquisitionResult.Acquired(
                            if (pageIndex == 0) byteArrayOf(1, 2) else byteArrayOf(3, 4, 5),
                        )
                    },
                liveInstall =
                    LiveTuneInstallOperation { _, _, pages ->
                        installCalls += 1
                        installedPages = pages.map { it.copyOf() }
                        LiveTuneInstallResult.Installed(4_242L)
                    },
            )

        val result =
            loader.load(
                signatureResponse = "exact.signature".toByteArray(),
                signatureLimit = 256,
                channel = emptyChannel(),
                timeoutMs = 250,
            )

        assertEquals(UsbReadOnlyTuneLoadResult.Loaded(4_242L, 2), result)
        assertEquals(listOf(0, 1), requestedPages)
        assertEquals(1, installCalls)
        assertEquals(2, installedPages.size)
        assertArrayEquals(byteArrayOf(1, 2), installedPages[0])
        assertArrayEquals(byteArrayOf(3, 4, 5), installedPages[1])
    }

    @Test
    fun failure_on_page_one_stops_and_never_installs_partial_page_set() {
        val requestedPages = mutableListOf<Int>()
        var installCalls = 0
        val loader =
            UsbReadOnlyTuneLoader(
                metadataAuthority = ReadOnlyTuneMetadataAuthority { _, _ ->
                    ReadOnlyTunePageCountResult.Available(3)
                },
                pageAcquisition =
                    ReadOnlyPageAcquisitionOperation { _, _, pageIndex, _, _ ->
                        requestedPages += pageIndex
                        if (pageIndex == 1) {
                            UsbReadOnlyPageAcquisitionResult.TransportFailure("link ended")
                        } else {
                            UsbReadOnlyPageAcquisitionResult.Acquired(byteArrayOf(pageIndex.toByte()))
                        }
                    },
                liveInstall =
                    LiveTuneInstallOperation { _, _, _ ->
                        installCalls += 1
                        LiveTuneInstallResult.Installed(1L)
                    },
            )

        val result =
            loader.load("sig".toByteArray(), 64, emptyChannel(), 100)

        assertEquals(
            UsbReadOnlyTuneLoadResult.TransportFailure(1, "link ended"),
            result,
        )
        assertEquals(listOf(0, 1), requestedPages)
        assertEquals(0, installCalls)
    }

    @Test
    fun metadata_rejection_performs_no_page_io_and_no_install() {
        var acquisitionCalls = 0
        var installCalls = 0
        val loader =
            UsbReadOnlyTuneLoader(
                metadataAuthority = ReadOnlyTuneMetadataAuthority { _, _ ->
                    ReadOnlyTunePageCountResult.Rejected(
                        ReadOnlyPageAuthorityRejection.UnknownController,
                    )
                },
                pageAcquisition =
                    ReadOnlyPageAcquisitionOperation { _, _, _, _, _ ->
                        acquisitionCalls += 1
                        UsbReadOnlyPageAcquisitionResult.Acquired(byteArrayOf())
                    },
                liveInstall =
                    LiveTuneInstallOperation { _, _, _ ->
                        installCalls += 1
                        LiveTuneInstallResult.Installed(1L)
                    },
            )

        val result = loader.load("unknown".toByteArray(), 64, emptyChannel(), 100)

        assertEquals(
            UsbReadOnlyTuneLoadResult.AuthorityRejected(
                ReadOnlyPageAuthorityRejection.UnknownController,
            ),
            result,
        )
        assertEquals(0, acquisitionCalls)
        assertEquals(0, installCalls)
    }

    @Test
    fun install_rejection_happens_only_after_all_pages_were_acquired() {
        val requestedPages = mutableListOf<Int>()
        val loader =
            UsbReadOnlyTuneLoader(
                metadataAuthority = ReadOnlyTuneMetadataAuthority { _, _ ->
                    ReadOnlyTunePageCountResult.Available(2)
                },
                pageAcquisition =
                    ReadOnlyPageAcquisitionOperation { _, _, pageIndex, _, _ ->
                        requestedPages += pageIndex
                        UsbReadOnlyPageAcquisitionResult.Acquired(byteArrayOf(pageIndex.toByte()))
                    },
                liveInstall =
                    LiveTuneInstallOperation { _, _, _ ->
                        LiveTuneInstallResult.Rejected(LiveTuneInstallRejection.DecodeFailure)
                    },
            )

        val result = loader.load("sig".toByteArray(), 64, emptyChannel(), 100)

        assertEquals(listOf(0, 1), requestedPages)
        assertEquals(
            UsbReadOnlyTuneLoadResult.InstallRejected(LiveTuneInstallRejection.DecodeFailure),
            result,
        )
    }

    @Test
    fun invalid_timeout_fails_before_metadata_or_page_work() {
        var metadataCalls = 0
        var acquisitionCalls = 0
        var installCalls = 0
        val loader =
            UsbReadOnlyTuneLoader(
                metadataAuthority = ReadOnlyTuneMetadataAuthority { _, _ ->
                    metadataCalls += 1
                    ReadOnlyTunePageCountResult.Available(1)
                },
                pageAcquisition =
                    ReadOnlyPageAcquisitionOperation { _, _, _, _, _ ->
                        acquisitionCalls += 1
                        UsbReadOnlyPageAcquisitionResult.Acquired(byteArrayOf(1))
                    },
                liveInstall =
                    LiveTuneInstallOperation { _, _, _ ->
                        installCalls += 1
                        LiveTuneInstallResult.Installed(1L)
                    },
            )

        assertEquals(
            UsbReadOnlyTuneLoadResult.TransportRejected(-1, "timeout must be positive"),
            loader.load("sig".toByteArray(), 64, emptyChannel(), 0),
        )
        assertEquals(0, metadataCalls)
        assertEquals(0, acquisitionCalls)
        assertEquals(0, installCalls)
    }

    private fun emptyChannel(): UsbReadOnlyTransactionChannel =
        UsbReadOnlyTransactionChannel(
            object : UsbReadOnlyBulkPort {
                override fun sendRequest(request: ByteArray, timeoutMs: Int): Int = request.size

                override fun receiveResponse(buffer: ByteArray, timeoutMs: Int): Int = 0

                override fun close() = Unit
            },
        )
}
