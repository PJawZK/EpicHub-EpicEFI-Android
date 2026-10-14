package com.epicefi.epichub.engine

import java.nio.ByteBuffer

private const val META_INVALID_HANDLE = -1
private const val META_NOT_DIRECT = -2
private const val META_INVALID_LENGTH = -3
private const val META_INVALID_LIMIT = -4
private const val META_EMPTY_SIGNATURE = -5
private const val META_INVALID_UTF8 = -6
private const val META_TOO_LARGE = -7
private const val META_UNKNOWN_CONTROLLER = -8
private const val META_RECOGNIZED_NO_PROFILE = -9
private const val META_MISSING_BUNDLE = -10
private const val META_REGISTRY_ERROR = -17

sealed interface ReadOnlyTunePageCountResult {
    data class Available(val pageCount: Int) : ReadOnlyTunePageCountResult

    data class Rejected(val reason: ReadOnlyPageAuthorityRejection) : ReadOnlyTunePageCountResult
}

internal fun interface ReadOnlyTuneMetadataAuthority {
    fun pageCount(
        signatureResponse: ByteArray,
        signatureLimit: Int,
    ): ReadOnlyTunePageCountResult
}

internal fun interface NativeReadOnlyTuneMetadataOps {
    fun pageCount(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
    ): Int
}

internal class NativeReadOnlyTuneMetadataAuthority(
    private val handleProvider: () -> Long,
    private val ops: NativeReadOnlyTuneMetadataOps = JniNativeReadOnlyTuneMetadataOps,
) : ReadOnlyTuneMetadataAuthority {
    override fun pageCount(
        signatureResponse: ByteArray,
        signatureLimit: Int,
    ): ReadOnlyTunePageCountResult {
        val handle = handleProvider()
        if (handle == 0L) {
            return rejected(ReadOnlyPageAuthorityRejection.InvalidHandle)
        }
        if (signatureLimit <= 0) {
            return rejected(ReadOnlyPageAuthorityRejection.InvalidResponseLimit)
        }
        if (signatureResponse.size > signatureLimit) {
            return rejected(ReadOnlyPageAuthorityRejection.ResponseTooLarge)
        }
        val buffer =
            ByteBuffer.allocateDirect(signatureResponse.size).apply {
                put(signatureResponse)
                rewind()
            }
        val result = ops.pageCount(handle, buffer, signatureResponse.size, signatureLimit)
        return if (result > 0) {
            ReadOnlyTunePageCountResult.Available(result)
        } else {
            rejected(mapStatus(result))
        }
    }

    private fun mapStatus(status: Int): ReadOnlyPageAuthorityRejection =
        when (status) {
            META_INVALID_HANDLE -> ReadOnlyPageAuthorityRejection.InvalidHandle
            META_NOT_DIRECT -> ReadOnlyPageAuthorityRejection.NotDirectBuffer
            META_INVALID_LENGTH -> ReadOnlyPageAuthorityRejection.InvalidLength
            META_INVALID_LIMIT -> ReadOnlyPageAuthorityRejection.InvalidResponseLimit
            META_EMPTY_SIGNATURE -> ReadOnlyPageAuthorityRejection.EmptySignature
            META_INVALID_UTF8 -> ReadOnlyPageAuthorityRejection.InvalidUtf8
            META_TOO_LARGE -> ReadOnlyPageAuthorityRejection.ResponseTooLarge
            META_UNKNOWN_CONTROLLER -> ReadOnlyPageAuthorityRejection.UnknownController
            META_RECOGNIZED_NO_PROFILE -> ReadOnlyPageAuthorityRejection.RecognizedControllerWithoutProfile
            META_MISSING_BUNDLE -> ReadOnlyPageAuthorityRejection.MissingCompiledBundle
            META_REGISTRY_ERROR -> ReadOnlyPageAuthorityRejection.RegistryFailure
            else -> ReadOnlyPageAuthorityRejection.NativeFailure
        }

    private fun rejected(reason: ReadOnlyPageAuthorityRejection) =
        ReadOnlyTunePageCountResult.Rejected(reason)
}

private object JniNativeReadOnlyTuneMetadataOps : NativeReadOnlyTuneMetadataOps {
    override fun pageCount(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
    ): Int =
        NativeReadOnlyTuneMetadataBridge.pageCount(
            handle,
            signatureBuffer,
            signatureLength,
            signatureLimit,
        )
}

object NativeReadOnlyTuneMetadataBridge {
    init {
        System.loadLibrary("epichub_engine")
    }

    external fun pageCount(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
    ): Int
}
