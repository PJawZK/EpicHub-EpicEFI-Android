package com.epicefi.epichub.engine

import java.nio.ByteBuffer

private const val MAX_GUARDED_BURN_REQUEST_BYTES = 65_541
private const val MAX_GUARDED_BURN_RESPONSE_BYTES = 65_536

private const val BURN_INVALID_HANDLE = -1
private const val BURN_NOT_DIRECT = -2
private const val BURN_INVALID_LENGTH = -3
private const val BURN_INVALID_LIMIT = -4
private const val BURN_EMPTY_SIGNATURE = -5
private const val BURN_INVALID_UTF8 = -6
private const val BURN_TOO_LARGE = -7
private const val BURN_UNKNOWN_CONTROLLER = -8
private const val BURN_RECOGNIZED_NO_PROFILE = -9
private const val BURN_MISSING_BUNDLE = -10
private const val BURN_INVALID_ENGINE = -11
private const val BURN_MISSING_SOURCE = -12
private const val BURN_NON_LIVE_SOURCE = -13
private const val BURN_RAM_NOT_CURRENT = -14
private const val BURN_STALE_PROFILE = -15
private const val BURN_SOURCE_CHANGED = -16
private const val BURN_PLAN_FAILURE = -17
private const val BURN_OUTPUT_TOO_SMALL = -18
private const val BURN_PROTOCOL_FAILURE = -19
private const val BURN_CRC_FAILURE = -20
private const val BURN_INVALID_ARGUMENT = -21
private const val BURN_REGISTRY_FAILURE = -22

private const val STAGE_BURN = 0
private const val STAGE_CRC = 1

enum class GuardedBurnRejection {
    InvalidHandle,
    InvalidArgument,
    InvalidResponseLimit,
    EmptySignature,
    InvalidUtf8,
    ResponseTooLarge,
    UnknownController,
    RecognizedControllerWithoutProfile,
    MissingCompiledBundle,
    InvalidEngine,
    MissingSourceImage,
    NonLiveControllerSource,
    RamNotConfirmedForCurrentDraft,
    StaleProfileGeneration,
    SourceImageChanged,
    PlanFailure,
    OutputTooSmall,
    BurnProtocolFailure,
    CrcVerificationFailure,
    RegistryFailure,
    NativeFailure,
}

enum class GuardedBurnStage {
    Burn,
    Crc,
}

data class GuardedBurnMetadata(
    val profileGeneration: Long,
    val draftRevision: Long,
    val page: Int,
)

data class GuardedBurnStagePlan(
    val request: ByteArray,
    val expectedResponseBytes: Int,
)

sealed interface GuardedBurnCreateResult {
    data class Created(val plan: NativeGuardedBurn) : GuardedBurnCreateResult
    data class Rejected(val reason: GuardedBurnRejection) : GuardedBurnCreateResult
}

internal sealed interface GuardedBurnStageResult {
    data class Planned(val plan: GuardedBurnStagePlan) : GuardedBurnStageResult
    data class Rejected(val reason: GuardedBurnRejection) : GuardedBurnStageResult
}

internal sealed interface GuardedBurnVerifyResult {
    data object Verified : GuardedBurnVerifyResult
    data class Rejected(val reason: GuardedBurnRejection) : GuardedBurnVerifyResult
}

internal interface NativeGuardedBurnOps {
    fun create(
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        page: Int,
    ): Long

    fun close(handle: Long)
    fun isAlive(handle: Long): Boolean
    fun activeCount(): Int
    fun profileGeneration(handle: Long): Long
    fun draftRevision(handle: Long): Long
    fun page(handle: Long): Int

    fun fillStage(
        planHandle: Long,
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        stage: Int,
        requestOutput: ByteBuffer,
    ): Long

    fun verifyStage(
        planHandle: Long,
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        stage: Int,
        responseBuffer: ByteBuffer,
        responseLength: Int,
    ): Int
}

class NativeGuardedBurn private constructor(
    private val engineHandleProvider: () -> Long,
    private val registryHandleProvider: () -> Long,
    signatureResponse: ByteArray,
    private val signatureLimit: Int,
    private val ops: NativeGuardedBurnOps,
    private var handle: Long,
    val metadata: GuardedBurnMetadata,
) : AutoCloseable {
    private val signatureResponse = signatureResponse.copyOf()

    val isAlive: Boolean
        get() = handle > 0L && ops.isAlive(handle)

    @Synchronized
    internal fun stagePlan(stage: GuardedBurnStage): GuardedBurnStageResult {
        val currentHandle = handle
        if (currentHandle <= 0L) {
            return GuardedBurnStageResult.Rejected(GuardedBurnRejection.InvalidHandle)
        }

        val output = ByteBuffer.allocateDirect(MAX_GUARDED_BURN_REQUEST_BYTES)
        val packed =
            ops.fillStage(
                currentHandle,
                engineHandleProvider(),
                registryHandleProvider(),
                directBuffer(signatureResponse),
                signatureResponse.size,
                signatureLimit,
                stage.nativeCode(),
                output,
            )
        if (packed <= 0L) {
            return GuardedBurnStageResult.Rejected(mapStatus(packed.toInt()))
        }

        val requestLength = (packed ushr 32).toInt()
        val expectedResponseBytes = (packed and 0xffff_ffffL).toInt()
        if (
            requestLength <= 0 || requestLength > MAX_GUARDED_BURN_REQUEST_BYTES ||
                expectedResponseBytes <= 0 || expectedResponseBytes > MAX_GUARDED_BURN_RESPONSE_BYTES
        ) {
            return GuardedBurnStageResult.Rejected(GuardedBurnRejection.NativeFailure)
        }

        output.position(0)
        val request = ByteArray(requestLength)
        output.get(request)
        return GuardedBurnStageResult.Planned(
            GuardedBurnStagePlan(
                request = request,
                expectedResponseBytes = expectedResponseBytes,
            ),
        )
    }

    @Synchronized
    internal fun verifyStage(
        stage: GuardedBurnStage,
        responseFrame: ByteArray,
    ): GuardedBurnVerifyResult {
        val currentHandle = handle
        if (currentHandle <= 0L) {
            return GuardedBurnVerifyResult.Rejected(GuardedBurnRejection.InvalidHandle)
        }
        if (responseFrame.isEmpty() || responseFrame.size > MAX_GUARDED_BURN_RESPONSE_BYTES) {
            return GuardedBurnVerifyResult.Rejected(GuardedBurnRejection.InvalidArgument)
        }

        val status =
            ops.verifyStage(
                currentHandle,
                engineHandleProvider(),
                registryHandleProvider(),
                directBuffer(signatureResponse),
                signatureResponse.size,
                signatureLimit,
                stage.nativeCode(),
                directBuffer(responseFrame),
                responseFrame.size,
            )
        return if (status == 0) {
            GuardedBurnVerifyResult.Verified
        } else {
            GuardedBurnVerifyResult.Rejected(mapStatus(status))
        }
    }

    @Synchronized
    override fun close() {
        val currentHandle = handle
        if (currentHandle > 0L) {
            ops.close(currentHandle)
            handle = 0L
        }
    }

    companion object {
        fun create(
            engine: NativeEngine,
            registry: NativeProfileRegistry,
            signatureResponse: ByteArray,
            signatureLimit: Int,
            page: Int,
        ): GuardedBurnCreateResult =
            createWithOps(
                engineHandleProvider = engine::requireNativeHandle,
                registryHandleProvider = registry::readOnlyRegistryHandle,
                signatureResponse = signatureResponse,
                signatureLimit = signatureLimit,
                page = page,
                ops = JniNativeGuardedBurnOps,
            )

        internal fun createWithOps(
            engineHandleProvider: () -> Long,
            registryHandleProvider: () -> Long,
            signatureResponse: ByteArray,
            signatureLimit: Int,
            page: Int,
            ops: NativeGuardedBurnOps,
        ): GuardedBurnCreateResult {
            if (
                signatureResponse.isEmpty() || signatureLimit <= 0 ||
                    signatureResponse.size > signatureLimit || page <= 0
            ) {
                return GuardedBurnCreateResult.Rejected(GuardedBurnRejection.InvalidArgument)
            }

            val engineHandle = engineHandleProvider()
            val registryHandle = registryHandleProvider()
            if (engineHandle <= 0L || registryHandle <= 0L) {
                return GuardedBurnCreateResult.Rejected(GuardedBurnRejection.InvalidHandle)
            }

            val handle =
                ops.create(
                    engineHandle,
                    registryHandle,
                    directBuffer(signatureResponse),
                    signatureResponse.size,
                    signatureLimit,
                    page,
                )
            if (handle <= 0L) {
                return GuardedBurnCreateResult.Rejected(mapStatus(handle.toInt()))
            }

            val generation = ops.profileGeneration(handle)
            val revision = ops.draftRevision(handle)
            val nativePage = ops.page(handle)
            if (generation <= 0L || revision < 0L || nativePage <= 0) {
                ops.close(handle)
                val status =
                    when {
                        generation < 0L -> generation.toInt()
                        revision < 0L -> revision.toInt()
                        nativePage < 0 -> nativePage
                        else -> BURN_INVALID_ARGUMENT
                    }
                return GuardedBurnCreateResult.Rejected(mapStatus(status))
            }

            return GuardedBurnCreateResult.Created(
                NativeGuardedBurn(
                    engineHandleProvider = engineHandleProvider,
                    registryHandleProvider = registryHandleProvider,
                    signatureResponse = signatureResponse,
                    signatureLimit = signatureLimit,
                    ops = ops,
                    handle = handle,
                    metadata = GuardedBurnMetadata(generation, revision, nativePage),
                ),
            )
        }

        fun activeCount(): Int = JniNativeGuardedBurnOps.activeCount()

        private fun directBuffer(bytes: ByteArray): ByteBuffer =
            ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                rewind()
            }

        private fun mapStatus(status: Int): GuardedBurnRejection =
            when (status) {
                BURN_INVALID_HANDLE -> GuardedBurnRejection.InvalidHandle
                BURN_INVALID_LIMIT -> GuardedBurnRejection.InvalidResponseLimit
                BURN_EMPTY_SIGNATURE -> GuardedBurnRejection.EmptySignature
                BURN_INVALID_UTF8 -> GuardedBurnRejection.InvalidUtf8
                BURN_TOO_LARGE -> GuardedBurnRejection.ResponseTooLarge
                BURN_UNKNOWN_CONTROLLER -> GuardedBurnRejection.UnknownController
                BURN_RECOGNIZED_NO_PROFILE -> GuardedBurnRejection.RecognizedControllerWithoutProfile
                BURN_MISSING_BUNDLE -> GuardedBurnRejection.MissingCompiledBundle
                BURN_INVALID_ENGINE -> GuardedBurnRejection.InvalidEngine
                BURN_MISSING_SOURCE -> GuardedBurnRejection.MissingSourceImage
                BURN_NON_LIVE_SOURCE -> GuardedBurnRejection.NonLiveControllerSource
                BURN_RAM_NOT_CURRENT -> GuardedBurnRejection.RamNotConfirmedForCurrentDraft
                BURN_STALE_PROFILE -> GuardedBurnRejection.StaleProfileGeneration
                BURN_SOURCE_CHANGED -> GuardedBurnRejection.SourceImageChanged
                BURN_PLAN_FAILURE -> GuardedBurnRejection.PlanFailure
                BURN_OUTPUT_TOO_SMALL -> GuardedBurnRejection.OutputTooSmall
                BURN_PROTOCOL_FAILURE -> GuardedBurnRejection.BurnProtocolFailure
                BURN_CRC_FAILURE -> GuardedBurnRejection.CrcVerificationFailure
                BURN_REGISTRY_FAILURE -> GuardedBurnRejection.RegistryFailure
                BURN_NOT_DIRECT,
                BURN_INVALID_LENGTH,
                BURN_INVALID_ARGUMENT,
                -> GuardedBurnRejection.InvalidArgument
                else -> GuardedBurnRejection.NativeFailure
            }

        private fun GuardedBurnStage.nativeCode(): Int =
            when (this) {
                GuardedBurnStage.Burn -> STAGE_BURN
                GuardedBurnStage.Crc -> STAGE_CRC
            }
    }
}

private object JniNativeGuardedBurnOps : NativeGuardedBurnOps {
    override fun create(
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        page: Int,
    ): Long =
        NativeGuardedBurnBridge.create(
            engineHandle,
            registryHandle,
            signatureBuffer,
            signatureLength,
            signatureLimit,
            page,
        )

    override fun close(handle: Long) = NativeGuardedBurnBridge.close(handle)
    override fun isAlive(handle: Long): Boolean = NativeGuardedBurnBridge.isAlive(handle)
    override fun activeCount(): Int = NativeGuardedBurnBridge.activeCount()
    override fun profileGeneration(handle: Long): Long = NativeGuardedBurnBridge.profileGeneration(handle)
    override fun draftRevision(handle: Long): Long = NativeGuardedBurnBridge.draftRevision(handle)
    override fun page(handle: Long): Int = NativeGuardedBurnBridge.page(handle)

    override fun fillStage(
        planHandle: Long,
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        stage: Int,
        requestOutput: ByteBuffer,
    ): Long =
        NativeGuardedBurnBridge.fillStage(
            planHandle,
            engineHandle,
            registryHandle,
            signatureBuffer,
            signatureLength,
            signatureLimit,
            stage,
            requestOutput,
        )

    override fun verifyStage(
        planHandle: Long,
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        stage: Int,
        responseBuffer: ByteBuffer,
        responseLength: Int,
    ): Int =
        NativeGuardedBurnBridge.verifyStage(
            planHandle,
            engineHandle,
            registryHandle,
            signatureBuffer,
            signatureLength,
            signatureLimit,
            stage,
            responseBuffer,
            responseLength,
        )
}

object NativeGuardedBurnBridge {
    init {
        System.loadLibrary("epichub_engine")
    }

    external fun create(
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        page: Int,
    ): Long

    external fun close(handle: Long)
    external fun isAlive(handle: Long): Boolean
    external fun activeCount(): Int
    external fun profileGeneration(handle: Long): Long
    external fun draftRevision(handle: Long): Long
    external fun page(handle: Long): Int

    external fun fillStage(
        planHandle: Long,
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        stage: Int,
        requestOutput: ByteBuffer,
    ): Long

    external fun verifyStage(
        planHandle: Long,
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        stage: Int,
        responseBuffer: ByteBuffer,
        responseLength: Int,
    ): Int
}
