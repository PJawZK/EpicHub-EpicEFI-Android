package com.epicefi.epichub.engine

data class ValidatedRuntimeControllerIdentity(
    val signature: String,
)

enum class RuntimeIdentityValidationError {
    INVALID_RESPONSE_LIMIT,
    RESPONSE_TOO_LARGE,
    EMPTY_SIGNATURE,
    INVALID_UTF8,
    JNI_ERROR,
    INTERNAL_ERROR,
    NATIVE_PANIC,
    UNKNOWN_STATUS,
}

sealed interface RuntimeIdentityValidationResult {
    data class Valid(val identity: ValidatedRuntimeControllerIdentity) : RuntimeIdentityValidationResult

    data class Rejected(
        val error: RuntimeIdentityValidationError,
        val nativeStatus: Int,
    ) : RuntimeIdentityValidationResult
}

fun interface RuntimeIdentityResponseValidator {
    fun validate(
        response: ByteArray,
        maxResponseBytes: Int,
    ): RuntimeIdentityValidationResult
}

object NativeRuntimeIdentityValidator : RuntimeIdentityResponseValidator {
    override fun validate(
        response: ByteArray,
        maxResponseBytes: Int,
    ): RuntimeIdentityValidationResult {
        val status = NativeEngineBridge.validateRuntimeIdentityResponse(response, maxResponseBytes)
        return runtimeIdentityValidationFromNativeStatus(status, response)
    }
}

internal fun runtimeIdentityValidationFromNativeStatus(
    status: Int,
    response: ByteArray,
): RuntimeIdentityValidationResult =
    when (status) {
        0 ->
            RuntimeIdentityValidationResult.Valid(
                ValidatedRuntimeControllerIdentity(signature = response.toString(Charsets.UTF_8)),
            )
        1 -> rejected(RuntimeIdentityValidationError.INVALID_RESPONSE_LIMIT, status)
        2 -> rejected(RuntimeIdentityValidationError.RESPONSE_TOO_LARGE, status)
        3 -> rejected(RuntimeIdentityValidationError.EMPTY_SIGNATURE, status)
        4 -> rejected(RuntimeIdentityValidationError.INVALID_UTF8, status)
        5 -> rejected(RuntimeIdentityValidationError.JNI_ERROR, status)
        6 -> rejected(RuntimeIdentityValidationError.INTERNAL_ERROR, status)
        255 -> rejected(RuntimeIdentityValidationError.NATIVE_PANIC, status)
        else -> rejected(RuntimeIdentityValidationError.UNKNOWN_STATUS, status)
    }

private fun rejected(
    error: RuntimeIdentityValidationError,
    status: Int,
): RuntimeIdentityValidationResult.Rejected =
    RuntimeIdentityValidationResult.Rejected(
        error = error,
        nativeStatus = status,
    )
