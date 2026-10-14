package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeLiveTuneInstallerTest {
    @Test
    fun successful_install_forwards_exact_signature_and_complete_direct_pages() {
        val ops = FakeOps(result = 4_242L)
        val installer = NativeLiveTuneInstaller({ 11L }, { 22L }, ops)
        val pages = listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5))

        val result = installer.install("exact.signature".toByteArray(), 256, pages)

        assertEquals(LiveTuneInstallResult.Installed(4_242L), result)
        assertEquals(11L, ops.engineHandle)
        assertEquals(22L, ops.registryHandle)
        assertEquals(15, ops.signatureLength)
        assertEquals(256, ops.signatureLimit)
        assertTrue(ops.signatureDirect)
        assertArrayEquals("exact.signature".toByteArray(), ops.signatureBytes)
        assertEquals(listOf(3, 2), ops.pageSizes)
        assertTrue(ops.pagesAllDirect)
        assertArrayEquals(byteArrayOf(1, 2, 3), ops.pageBytes[0])
        assertArrayEquals(byteArrayOf(4, 5), ops.pageBytes[1])
    }

    @Test
    fun every_native_rejection_category_remains_explicit() {
        val cases =
            listOf(
                -1L to LiveTuneInstallRejection.InvalidEngineHandle,
                -2L to LiveTuneInstallRejection.InvalidRegistryHandle,
                -3L to LiveTuneInstallRejection.NotDirectBuffer,
                -4L to LiveTuneInstallRejection.InvalidLength,
                -5L to LiveTuneInstallRejection.InvalidResponseLimit,
                -6L to LiveTuneInstallRejection.EmptySignature,
                -7L to LiveTuneInstallRejection.InvalidUtf8,
                -8L to LiveTuneInstallRejection.ResponseTooLarge,
                -9L to LiveTuneInstallRejection.UnknownController,
                -10L to LiveTuneInstallRejection.RecognizedControllerWithoutProfile,
                -11L to LiveTuneInstallRejection.MissingCompiledBundle,
                -12L to LiveTuneInstallRejection.InvalidPages,
                -13L to LiveTuneInstallRejection.DecodeFailure,
                -14L to LiveTuneInstallRejection.RegistryFailure,
                -255L to LiveTuneInstallRejection.NativeFailure,
                0L to LiveTuneInstallRejection.NativeFailure,
            )

        cases.forEach { (nativeResult, expected) ->
            val result =
                NativeLiveTuneInstaller({ 1L }, { 2L }, FakeOps(nativeResult))
                    .install("signature".toByteArray(), 64, listOf(byteArrayOf(1)))
            assertEquals(LiveTuneInstallResult.Rejected(expected), result)
        }
    }

    @Test
    fun invalid_local_authority_fails_before_native_call() {
        val badLimitOps = FakeOps(1L)
        val badLimit = NativeLiveTuneInstaller({ 1L }, { 2L }, badLimitOps)
        assertEquals(
            LiveTuneInstallResult.Rejected(LiveTuneInstallRejection.InvalidResponseLimit),
            badLimit.install("sig".toByteArray(), 0, emptyList()),
        )
        assertEquals(0, badLimitOps.calls)

        val closedEngineOps = FakeOps(1L)
        val closedEngine = NativeLiveTuneInstaller({ 0L }, { 2L }, closedEngineOps)
        assertEquals(
            LiveTuneInstallResult.Rejected(LiveTuneInstallRejection.InvalidEngineHandle),
            closedEngine.install("sig".toByteArray(), 64, emptyList()),
        )
        assertEquals(0, closedEngineOps.calls)

        val closedRegistryOps = FakeOps(1L)
        val closedRegistry = NativeLiveTuneInstaller({ 1L }, { 0L }, closedRegistryOps)
        assertEquals(
            LiveTuneInstallResult.Rejected(LiveTuneInstallRejection.InvalidRegistryHandle),
            closedRegistry.install("sig".toByteArray(), 64, emptyList()),
        )
        assertEquals(0, closedRegistryOps.calls)
    }

    private class FakeOps(
        private val result: Long,
    ) : NativeLiveTuneInstallOps {
        var calls = 0
        var engineHandle = -1L
        var registryHandle = -1L
        var signatureLength = -1
        var signatureLimit = -1
        var signatureDirect = false
        var signatureBytes = byteArrayOf()
        var pageSizes = emptyList<Int>()
        var pagesAllDirect = false
        var pageBytes = emptyList<ByteArray>()

        override fun installLivePages(
            engineHandle: Long,
            registryHandle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
            pages: Array<ByteBuffer>,
        ): Long {
            calls += 1
            this.engineHandle = engineHandle
            this.registryHandle = registryHandle
            this.signatureLength = signatureLength
            this.signatureLimit = signatureLimit
            signatureDirect = signatureBuffer.isDirect
            signatureBytes = ByteArray(signatureLength).also { signatureBuffer.duplicate().get(it) }
            pageSizes = pages.map(ByteBuffer::capacity)
            pagesAllDirect = pages.all(ByteBuffer::isDirect)
            pageBytes =
                pages.map { page ->
                    ByteArray(page.capacity()).also { page.duplicate().get(it) }
                }
            return result
        }
    }
}
