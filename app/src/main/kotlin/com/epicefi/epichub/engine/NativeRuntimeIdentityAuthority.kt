package com.epicefi.epichub.engine

import java.nio.ByteBuffer

internal const val RUNTIME_SIGNATURE_STATUS_OK = 0
internal const val RUNTIME_SIGNATURE_STATUS_NOT_DIRECT = 1
internal const val RUNTIME_SIGNATURE_STATUS_INVALID_LENGTH = 2
internal const val RUNTIME_SIGNATURE_STATUS_INVALID_LIMIT = 3
internal const val RUNTIME_SIGNATURE_STATUS_EMPTY = 4
internal const val RUNTIME_SIGNATURE_STATUS_INVALID_UTF8 = 5
internal const val RUNTIME_SIGNATURE_STATUS_TOO_LARGE = 6
internal const val RUNTIME_SIGNATURE_STATUS_PANIC = 255

data class ValidatedRuntimeIdentity(
    val signature: String,
)

enum class RuntimeIdentityRejection {
    NotDirectBuffer,
    InvalidLength,
    InvalidResponseLimit,
    EmptySignature,
    InvalidUtf8,
    ResponseTooLarge,
    NativeFailure,
}

sealed interface RuntimeIdentityValidationResult {
    data class Accepted(
        val identity: ValidatedRuntimeIdentity,
    ) : RuntimeIdentityValidationResult

    data class Rejected(
        val reason: RuntimeIdentityRejection,
    ) : RuntimeIdentityValidationResult
}

fun interface RuntimeIdentityResponseAuthority {
    fun validate(
        response: ByteArray,
        maxResponseBytes: Int,
    ): RuntimeIdentityValidationResult
}

internal fun interface RuntimeSignatureValidator {
    fun validate(
        buffer: ByteBuffer,
        responseLength: Int,
        maxResponseBytes: Int,
    ): Int
}

class NativeRuntimeIdentityAuthority internal constructor(
    private val validator: RuntimeSignatureValidator,
) : RuntimeIdentityResponseAuthority {
    constructor() : this(
        RuntimeSignatureValidator { buffer, responseLength, maxResponseBytes ->
            NativeRuntimeIdentityBridge.validateRuntimeSignatureResponse(
                buffer,
                responseLength,
                maxResponseBytes,
            )
        },
    )

    override fun validate(
        response: ByteArray,
        maxResponseBytes: Int,
    ): RuntimeIdentityValidationResult {
        val direct = ByteBuffer.allocateDirect(response.size).apply {
            put(response)
            rewind()
        }
        return when (validator.validate(direct, response.size, maxResponseBytes)) {
            RUNTIME_SIGNATURE_STATUS_OK ->
                RuntimeIdentityValidationResult.Accepted(
                    ValidatedRuntimeIdentity(response.toString(Charsets.UTF_8)),
                )
            RUNTIME_SIGNATURE_STATUS_NOT_DIRECT -> rejected(RuntimeIdentityRejection.NotDirectBuffer)
            RUNTIME_SIGNATURE_STATUS_INVALID_LENGTH -> rejected(RuntimeIdentityRejection.InvalidLength)
            RUNTIME_SIGNATURE_STATUS_INVALID_LIMIT -> rejected(RuntimeIdentityRejection.InvalidResponseLimit)
            RUNTIME_SIGNATURE_STATUS_EMPTY -> rejected(RuntimeIdentityRejection.EmptySignature)
            RUNTIME_SIGNATURE_STATUS_INVALID_UTF8 -> rejected(RuntimeIdentityRejection.InvalidUtf8)
            RUNTIME_SIGNATURE_STATUS_TOO_LARGE -> rejected(RuntimeIdentityRejection.ResponseTooLarge)
            else -> rejected(RuntimeIdentityRejection.NativeFailure)
        }
    }

    private fun rejected(reason: RuntimeIdentityRejection) =
        RuntimeIdentityValidationResult.Rejected(reason)
}

object NativeRuntimeIdentityBridge {
    init {
        System.loadLibrary("epichub_engine")
    }

    external fun validateRuntimeSignatureResponse(
        buffer: ByteBuffer,
        responseLength: Int,
        maxResponseBytes: Int,
    ): Int
}
