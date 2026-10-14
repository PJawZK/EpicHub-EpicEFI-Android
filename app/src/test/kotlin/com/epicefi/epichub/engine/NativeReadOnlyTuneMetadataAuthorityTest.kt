package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeReadOnlyTuneMetadataAuthorityTest {
    @Test
    fun positive_native_count_preserves_exact_signature_and_direct_buffer() {
        val ops = FakeOps(result = 2)
        val authority = NativeReadOnlyTuneMetadataAuthority({ 41L }, ops)

        val result = authority.pageCount("exact.signature".toByteArray(), 256)

        assertEquals(ReadOnlyTunePageCountResult.Available(2), result)
        assertEquals(41L, ops.handle)
        assertEquals(15, ops.signatureLength)
        assertEquals(256, ops.signatureLimit)
        assertTrue(ops.direct)
        assertArrayEquals("exact.signature".toByteArray(), ops.signature)
    }

    @Test
    fun native_authority_statuses_remain_explicit() {
        val cases =
            listOf(
                -1 to ReadOnlyPageAuthorityRejection.InvalidHandle,
                -2 to ReadOnlyPageAuthorityRejection.NotDirectBuffer,
                -3 to ReadOnlyPageAuthorityRejection.InvalidLength,
                -4 to ReadOnlyPageAuthorityRejection.InvalidResponseLimit,
                -5 to ReadOnlyPageAuthorityRejection.EmptySignature,
                -6 to ReadOnlyPageAuthorityRejection.InvalidUtf8,
                -7 to ReadOnlyPageAuthorityRejection.ResponseTooLarge,
                -8 to ReadOnlyPageAuthorityRejection.UnknownController,
                -9 to ReadOnlyPageAuthorityRejection.RecognizedControllerWithoutProfile,
                -10 to ReadOnlyPageAuthorityRejection.MissingCompiledBundle,
                -17 to ReadOnlyPageAuthorityRejection.RegistryFailure,
                -255 to ReadOnlyPageAuthorityRejection.NativeFailure,
                0 to ReadOnlyPageAuthorityRejection.NativeFailure,
            )

        cases.forEach { (status, expected) ->
            assertEquals(
                ReadOnlyTunePageCountResult.Rejected(expected),
                NativeReadOnlyTuneMetadataAuthority({ 1L }, FakeOps(status))
                    .pageCount("sig".toByteArray(), 64),
            )
        }
    }

    @Test
    fun invalid_local_authority_fails_before_native() {
        val closedOps = FakeOps(1)
        assertEquals(
            ReadOnlyTunePageCountResult.Rejected(ReadOnlyPageAuthorityRejection.InvalidHandle),
            NativeReadOnlyTuneMetadataAuthority({ 0L }, closedOps)
                .pageCount("sig".toByteArray(), 64),
        )
        assertEquals(0, closedOps.calls)

        val limitOps = FakeOps(1)
        assertEquals(
            ReadOnlyTunePageCountResult.Rejected(
                ReadOnlyPageAuthorityRejection.InvalidResponseLimit,
            ),
            NativeReadOnlyTuneMetadataAuthority({ 1L }, limitOps)
                .pageCount("sig".toByteArray(), 0),
        )
        assertEquals(0, limitOps.calls)
    }

    private class FakeOps(
        private val result: Int,
    ) : NativeReadOnlyTuneMetadataOps {
        var calls = 0
        var handle = -1L
        var signatureLength = -1
        var signatureLimit = -1
        var direct = false
        var signature = byteArrayOf()

        override fun pageCount(
            handle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
        ): Int {
            calls += 1
            this.handle = handle
            this.signatureLength = signatureLength
            this.signatureLimit = signatureLimit
            direct = signatureBuffer.isDirect
            signature = ByteArray(signatureLength).also { signatureBuffer.duplicate().get(it) }
            return result
        }
    }
}
