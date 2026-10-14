package com.epicefi.epichub.engine

import java.nio.ByteBuffer

private const val PROFILE_RESULT_INVALID_HANDLE = -1L
private const val PROFILE_RESULT_NOT_DIRECT = -2L
private const val PROFILE_RESULT_INVALID_LENGTH = -3L
private const val PROFILE_RESULT_INVALID_LIMIT = -4L
private const val PROFILE_RESULT_EMPTY_SIGNATURE = -5L
private const val PROFILE_RESULT_INVALID_UTF8 = -6L
private const val PROFILE_RESULT_TOO_LARGE = -7L
private const val PROFILE_RESULT_UNKNOWN_CONTROLLER = -8L
private const val PROFILE_RESULT_RECOGNIZED_NO_PROFILE = -9L
private const val PROFILE_RESULT_REGISTRY_ERROR = -10L

enum class RuntimeProfileSelectionRejection {
    InvalidHandle,
    NotDirectBuffer,
    InvalidLength,
    InvalidResponseLimit,
    EmptySignature,
    InvalidUtf8,
    ResponseTooLarge,
    UnknownController,
    RecognizedControllerWithoutProfile,
    RegistryFailure,
    NativeFailure,
}

sealed interface RuntimeProfileSelectionResult {
    data class Selected(
        val profileGeneration: Long,
    ) : RuntimeProfileSelectionResult

    data class Rejected(
        val reason: RuntimeProfileSelectionRejection,
    ) : RuntimeProfileSelectionResult
}

internal interface NativeProfileRegistryOps {
    fun create(): Long

    fun close(handle: Long)

    fun isAlive(handle: Long): Boolean

    fun activeCount(): Int

    fun selectRuntimeProfileGeneration(
        handle: Long,
        buffer: ByteBuffer,
        responseLength: Int,
        maxResponseBytes: Int,
    ): Long
}

class NativeProfileRegistry internal constructor(
    private val ops: NativeProfileRegistryOps,
) : AutoCloseable {
    constructor() : this(JniNativeProfileRegistryOps)

    private var handle: Long = ops.create()

    val isAlive: Boolean
        get() = handle != 0L && ops.isAlive(handle)

    fun select(
        response: ByteArray,
        maxResponseBytes: Int,
    ): RuntimeProfileSelectionResult {
        val currentHandle = handle
        if (currentHandle == 0L) {
            return RuntimeProfileSelectionResult.Rejected(
                RuntimeProfileSelectionRejection.InvalidHandle,
            )
        }
        val direct = ByteBuffer.allocateDirect(response.size).apply {
            put(response)
            rewind()
        }
        val nativeResult =
            ops.selectRuntimeProfileGeneration(
                currentHandle,
                direct,
                response.size,
                maxResponseBytes,
            )
        return when {
            nativeResult > 0L -> RuntimeProfileSelectionResult.Selected(nativeResult)
            nativeResult == PROFILE_RESULT_INVALID_HANDLE -> rejected(RuntimeProfileSelectionRejection.InvalidHandle)
            nativeResult == PROFILE_RESULT_NOT_DIRECT -> rejected(RuntimeProfileSelectionRejection.NotDirectBuffer)
            nativeResult == PROFILE_RESULT_INVALID_LENGTH -> rejected(RuntimeProfileSelectionRejection.InvalidLength)
            nativeResult == PROFILE_RESULT_INVALID_LIMIT -> rejected(RuntimeProfileSelectionRejection.InvalidResponseLimit)
            nativeResult == PROFILE_RESULT_EMPTY_SIGNATURE -> rejected(RuntimeProfileSelectionRejection.EmptySignature)
            nativeResult == PROFILE_RESULT_INVALID_UTF8 -> rejected(RuntimeProfileSelectionRejection.InvalidUtf8)
            nativeResult == PROFILE_RESULT_TOO_LARGE -> rejected(RuntimeProfileSelectionRejection.ResponseTooLarge)
            nativeResult == PROFILE_RESULT_UNKNOWN_CONTROLLER -> rejected(RuntimeProfileSelectionRejection.UnknownController)
            nativeResult == PROFILE_RESULT_RECOGNIZED_NO_PROFILE -> rejected(RuntimeProfileSelectionRejection.RecognizedControllerWithoutProfile)
            nativeResult == PROFILE_RESULT_REGISTRY_ERROR -> rejected(RuntimeProfileSelectionRejection.RegistryFailure)
            else -> rejected(RuntimeProfileSelectionRejection.NativeFailure)
        }
    }

    override fun close() {
        val currentHandle = handle
        if (currentHandle != 0L) {
            ops.close(currentHandle)
            handle = 0L
        }
    }

    private fun rejected(reason: RuntimeProfileSelectionRejection) =
        RuntimeProfileSelectionResult.Rejected(reason)
}

private object JniNativeProfileRegistryOps : NativeProfileRegistryOps {
    override fun create(): Long = NativeProfileRegistryBridge.create()

    override fun close(handle: Long) = NativeProfileRegistryBridge.close(handle)

    override fun isAlive(handle: Long): Boolean = NativeProfileRegistryBridge.isAlive(handle)

    override fun activeCount(): Int = NativeProfileRegistryBridge.activeCount()

    override fun selectRuntimeProfileGeneration(
        handle: Long,
        buffer: ByteBuffer,
        responseLength: Int,
        maxResponseBytes: Int,
    ): Long =
        NativeProfileRegistryBridge.selectRuntimeProfileGeneration(
            handle,
            buffer,
            responseLength,
            maxResponseBytes,
        )
}

object NativeProfileRegistryBridge {
    init {
        System.loadLibrary("epichub_engine")
    }

    external fun create(): Long

    external fun close(handle: Long)

    external fun isAlive(handle: Long): Boolean

    external fun activeCount(): Int

    external fun selectRuntimeProfileGeneration(
        handle: Long,
        buffer: ByteBuffer,
        responseLength: Int,
        maxResponseBytes: Int,
    ): Long
}
