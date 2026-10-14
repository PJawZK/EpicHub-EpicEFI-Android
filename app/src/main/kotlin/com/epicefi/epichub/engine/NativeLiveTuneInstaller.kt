package com.epicefi.epichub.engine

import java.nio.ByteBuffer

private const val INSTALL_INVALID_ENGINE_HANDLE = -1L
private const val INSTALL_INVALID_REGISTRY_HANDLE = -2L
private const val INSTALL_NOT_DIRECT = -3L
private const val INSTALL_INVALID_LENGTH = -4L
private const val INSTALL_INVALID_LIMIT = -5L
private const val INSTALL_EMPTY_SIGNATURE = -6L
private const val INSTALL_INVALID_UTF8 = -7L
private const val INSTALL_TOO_LARGE = -8L
private const val INSTALL_UNKNOWN_CONTROLLER = -9L
private const val INSTALL_RECOGNIZED_NO_PROFILE = -10L
private const val INSTALL_MISSING_BUNDLE = -11L
private const val INSTALL_INVALID_PAGES = -12L
private const val INSTALL_DECODE_FAILURE = -13L
private const val INSTALL_REGISTRY_FAILURE = -14L

enum class LiveTuneInstallRejection {
    InvalidEngineHandle,
    InvalidRegistryHandle,
    NotDirectBuffer,
    InvalidLength,
    InvalidResponseLimit,
    EmptySignature,
    InvalidUtf8,
    ResponseTooLarge,
    UnknownController,
    RecognizedControllerWithoutProfile,
    MissingCompiledBundle,
    InvalidPages,
    DecodeFailure,
    RegistryFailure,
    NativeFailure,
}

sealed interface LiveTuneInstallResult {
    data class Installed(val profileGeneration: Long) : LiveTuneInstallResult

    data class Rejected(val reason: LiveTuneInstallRejection) : LiveTuneInstallResult
}

internal interface NativeLiveTuneInstallOps {
    fun installLivePages(
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        pages: Array<ByteBuffer>,
    ): Long
}

class NativeLiveTuneInstaller internal constructor(
    private val engineHandleProvider: () -> Long,
    private val registryHandleProvider: () -> Long,
    private val ops: NativeLiveTuneInstallOps,
) {
    constructor(engine: NativeEngine, registry: NativeProfileRegistry) : this(
        engine::requireNativeHandle,
        registry::readOnlyRegistryHandle,
        JniNativeLiveTuneInstallOps,
    )

    fun install(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        pages: List<ByteArray>,
    ): LiveTuneInstallResult {
        if (signatureLimit <= 0) {
            return rejected(LiveTuneInstallRejection.InvalidResponseLimit)
        }
        if (signatureResponse.size > signatureLimit) {
            return rejected(LiveTuneInstallRejection.ResponseTooLarge)
        }

        val engineHandle = runCatching(engineHandleProvider).getOrElse {
            return rejected(LiveTuneInstallRejection.InvalidEngineHandle)
        }
        if (engineHandle == 0L) {
            return rejected(LiveTuneInstallRejection.InvalidEngineHandle)
        }
        val registryHandle = registryHandleProvider()
        if (registryHandle == 0L) {
            return rejected(LiveTuneInstallRejection.InvalidRegistryHandle)
        }

        val signatureBuffer = directBuffer(signatureResponse)
        val pageBuffers = Array(pages.size) { index -> directBuffer(pages[index]) }
        val result =
            ops.installLivePages(
                engineHandle,
                registryHandle,
                signatureBuffer,
                signatureResponse.size,
                signatureLimit,
                pageBuffers,
            )
        return when {
            result > 0L -> LiveTuneInstallResult.Installed(result)
            result == INSTALL_INVALID_ENGINE_HANDLE -> rejected(LiveTuneInstallRejection.InvalidEngineHandle)
            result == INSTALL_INVALID_REGISTRY_HANDLE -> rejected(LiveTuneInstallRejection.InvalidRegistryHandle)
            result == INSTALL_NOT_DIRECT -> rejected(LiveTuneInstallRejection.NotDirectBuffer)
            result == INSTALL_INVALID_LENGTH -> rejected(LiveTuneInstallRejection.InvalidLength)
            result == INSTALL_INVALID_LIMIT -> rejected(LiveTuneInstallRejection.InvalidResponseLimit)
            result == INSTALL_EMPTY_SIGNATURE -> rejected(LiveTuneInstallRejection.EmptySignature)
            result == INSTALL_INVALID_UTF8 -> rejected(LiveTuneInstallRejection.InvalidUtf8)
            result == INSTALL_TOO_LARGE -> rejected(LiveTuneInstallRejection.ResponseTooLarge)
            result == INSTALL_UNKNOWN_CONTROLLER -> rejected(LiveTuneInstallRejection.UnknownController)
            result == INSTALL_RECOGNIZED_NO_PROFILE -> rejected(LiveTuneInstallRejection.RecognizedControllerWithoutProfile)
            result == INSTALL_MISSING_BUNDLE -> rejected(LiveTuneInstallRejection.MissingCompiledBundle)
            result == INSTALL_INVALID_PAGES -> rejected(LiveTuneInstallRejection.InvalidPages)
            result == INSTALL_DECODE_FAILURE -> rejected(LiveTuneInstallRejection.DecodeFailure)
            result == INSTALL_REGISTRY_FAILURE -> rejected(LiveTuneInstallRejection.RegistryFailure)
            else -> rejected(LiveTuneInstallRejection.NativeFailure)
        }
    }

    private fun directBuffer(bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            rewind()
        }

    private fun rejected(reason: LiveTuneInstallRejection) = LiveTuneInstallResult.Rejected(reason)
}

private object JniNativeLiveTuneInstallOps : NativeLiveTuneInstallOps {
    override fun installLivePages(
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        pages: Array<ByteBuffer>,
    ): Long =
        NativeLiveTuneInstallBridge.installLivePages(
            engineHandle,
            registryHandle,
            signatureBuffer,
            signatureLength,
            signatureLimit,
            pages,
        )
}

object NativeLiveTuneInstallBridge {
    init {
        System.loadLibrary("epichub_engine")
    }

    external fun installLivePages(
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        pages: Array<ByteBuffer>,
    ): Long
}
