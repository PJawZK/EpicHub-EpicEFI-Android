package com.epicefi.epichub.engine

import java.nio.ByteBuffer

private const val MAX_GUARDED_BATCH_REQUEST_BYTES = 65_541
private const val MAX_GUARDED_BATCH_RESPONSE_BYTES = 65_536

private const val BATCH_INVALID_HANDLE = -1
private const val BATCH_NOT_DIRECT = -2
private const val BATCH_INVALID_LENGTH = -3
private const val BATCH_INVALID_LIMIT = -4
private const val BATCH_EMPTY_SIGNATURE = -5
private const val BATCH_INVALID_UTF8 = -6
private const val BATCH_TOO_LARGE = -7
private const val BATCH_UNKNOWN_CONTROLLER = -8
private const val BATCH_RECOGNIZED_NO_PROFILE = -9
private const val BATCH_MISSING_BUNDLE = -10
private const val BATCH_INVALID_ENGINE = -11
private const val BATCH_MISSING_SOURCE = -12
private const val BATCH_NON_LIVE_SOURCE = -13
private const val BATCH_STALE_REVISION = -14
private const val BATCH_STALE_PROFILE = -15
private const val BATCH_SOURCE_CHANGED = -16
private const val BATCH_PLAN_FAILURE = -17
private const val BATCH_INVALID_TRANSACTION_INDEX = -18
private const val BATCH_OUTPUT_TOO_SMALL = -19
private const val BATCH_STALE_PRECONDITION = -20
private const val BATCH_WRITE_ACK_FAILURE = -21
private const val BATCH_READBACK_FAILURE = -22
private const val BATCH_READBACK_MISMATCH = -23
private const val BATCH_ENVELOPE_FAILURE = -24
private const val BATCH_INVALID_ARGUMENT = -25
private const val BATCH_REGISTRY_FAILURE = -26

private const val STAGE_PRECONDITION = 0
private const val STAGE_WRITE = 1
private const val STAGE_READBACK = 2

enum class GuardedWriteBatchRejection {
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
    StaleDraftRevision,
    StaleProfileGeneration,
    SourceImageChanged,
    PlanFailure,
    InvalidTransactionIndex,
    OutputTooSmall,
    StalePrecondition,
    WriteAcknowledgementFailure,
    ReadbackFailure,
    ReadbackMismatch,
    EnvelopeFailure,
    RegistryFailure,
    NativeFailure,
}

data class GuardedWriteBatchMetadata(
    val profileGeneration: Long,
    val draftRevision: Long,
    val transactionCount: Int,
)

sealed interface GuardedWriteBatchCreateResult {
    data class Created(
        val batch: NativeGuardedWriteBatch,
    ) : GuardedWriteBatchCreateResult

    data class Rejected(
        val reason: GuardedWriteBatchRejection,
    ) : GuardedWriteBatchCreateResult
}

sealed interface GuardedWriteBatchStageResult {
    data class Planned(
        val plan: GuardedRamWriteStagePlan,
    ) : GuardedWriteBatchStageResult

    data class Rejected(
        val reason: GuardedWriteBatchRejection,
    ) : GuardedWriteBatchStageResult
}

sealed interface GuardedWriteBatchVerifyResult {
    data object Verified : GuardedWriteBatchVerifyResult

    data class Rejected(
        val reason: GuardedWriteBatchRejection,
    ) : GuardedWriteBatchVerifyResult
}

internal interface NativeGuardedWriteBatchOps {
    fun create(
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        expectedDraftRevision: Long,
    ): Long

    fun close(handle: Long)

    fun isAlive(handle: Long): Boolean

    fun activeCount(): Int

    fun profileGeneration(handle: Long): Long

    fun draftRevision(handle: Long): Long

    fun transactionCount(handle: Long): Int

    fun fillStage(
        batchHandle: Long,
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        transactionIndex: Int,
        stage: Int,
        requestOutput: ByteBuffer,
    ): Long

    fun verifyStage(
        batchHandle: Long,
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        transactionIndex: Int,
        stage: Int,
        responseBuffer: ByteBuffer,
        responseLength: Int,
    ): Int
}

class NativeGuardedWriteBatch private constructor(
    private val engineHandleProvider: () -> Long,
    private val registryHandleProvider: () -> Long,
    signatureResponse: ByteArray,
    private val signatureLimit: Int,
    private val ops: NativeGuardedWriteBatchOps,
    private var handle: Long,
    val metadata: GuardedWriteBatchMetadata,
) : AutoCloseable {
    private val signatureResponse = signatureResponse.copyOf()

    val isAlive: Boolean
        get() = handle > 0L && ops.isAlive(handle)

    @Synchronized
    fun stagePlan(
        transactionIndex: Int,
        stage: GuardedRamWriteStage,
    ): GuardedWriteBatchStageResult {
        val currentHandle = handle
        if (currentHandle <= 0L) {
            return GuardedWriteBatchStageResult.Rejected(GuardedWriteBatchRejection.InvalidHandle)
        }
        if (transactionIndex < 0) {
            return GuardedWriteBatchStageResult.Rejected(GuardedWriteBatchRejection.InvalidTransactionIndex)
        }
        val output = ByteBuffer.allocateDirect(MAX_GUARDED_BATCH_REQUEST_BYTES)
        val packed =
            ops.fillStage(
                currentHandle,
                engineHandleProvider(),
                registryHandleProvider(),
                directBuffer(signatureResponse),
                signatureResponse.size,
                signatureLimit,
                transactionIndex,
                stage.nativeCode(),
                output,
            )
        if (packed <= 0L) {
            return GuardedWriteBatchStageResult.Rejected(mapStatus(packed.toInt()))
        }
        val requestLength = (packed ushr 32).toInt()
        val expectedResponseBytes = (packed and 0xffff_ffffL).toInt()
        if (
            requestLength <= 0 || requestLength > MAX_GUARDED_BATCH_REQUEST_BYTES ||
                expectedResponseBytes <= 0 || expectedResponseBytes > MAX_GUARDED_BATCH_RESPONSE_BYTES
        ) {
            return GuardedWriteBatchStageResult.Rejected(GuardedWriteBatchRejection.NativeFailure)
        }
        output.position(0)
        val request = ByteArray(requestLength)
        output.get(request)
        return GuardedWriteBatchStageResult.Planned(
            GuardedRamWriteStagePlan(request, expectedResponseBytes),
        )
    }

    @Synchronized
    fun verifyStage(
        transactionIndex: Int,
        stage: GuardedRamWriteStage,
        responseFrame: ByteArray,
    ): GuardedWriteBatchVerifyResult {
        val currentHandle = handle
        if (currentHandle <= 0L) {
            return GuardedWriteBatchVerifyResult.Rejected(GuardedWriteBatchRejection.InvalidHandle)
        }
        if (transactionIndex < 0) {
            return GuardedWriteBatchVerifyResult.Rejected(GuardedWriteBatchRejection.InvalidTransactionIndex)
        }
        if (responseFrame.isEmpty() || responseFrame.size > MAX_GUARDED_BATCH_RESPONSE_BYTES) {
            return GuardedWriteBatchVerifyResult.Rejected(GuardedWriteBatchRejection.InvalidArgument)
        }
        val status =
            ops.verifyStage(
                currentHandle,
                engineHandleProvider(),
                registryHandleProvider(),
                directBuffer(signatureResponse),
                signatureResponse.size,
                signatureLimit,
                transactionIndex,
                stage.nativeCode(),
                directBuffer(responseFrame),
                responseFrame.size,
            )
        return if (status == 0) {
            GuardedWriteBatchVerifyResult.Verified
        } else {
            GuardedWriteBatchVerifyResult.Rejected(mapStatus(status))
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
            expectedDraftRevision: Long,
        ): GuardedWriteBatchCreateResult =
            createWithOps(
                engineHandleProvider = engine::requireNativeHandle,
                registryHandleProvider = registry::readOnlyRegistryHandle,
                signatureResponse = signatureResponse,
                signatureLimit = signatureLimit,
                expectedDraftRevision = expectedDraftRevision,
                ops = JniNativeGuardedWriteBatchOps,
            )

        internal fun createWithOps(
            engineHandleProvider: () -> Long,
            registryHandleProvider: () -> Long,
            signatureResponse: ByteArray,
            signatureLimit: Int,
            expectedDraftRevision: Long,
            ops: NativeGuardedWriteBatchOps,
        ): GuardedWriteBatchCreateResult {
            if (
                signatureResponse.isEmpty() || signatureLimit <= 0 ||
                    signatureResponse.size > signatureLimit || expectedDraftRevision < 0L
            ) {
                return GuardedWriteBatchCreateResult.Rejected(GuardedWriteBatchRejection.InvalidArgument)
            }
            val engineHandle = engineHandleProvider()
            val registryHandle = registryHandleProvider()
            if (engineHandle <= 0L || registryHandle <= 0L) {
                return GuardedWriteBatchCreateResult.Rejected(GuardedWriteBatchRejection.InvalidHandle)
            }
            val handle =
                ops.create(
                    engineHandle,
                    registryHandle,
                    directBuffer(signatureResponse),
                    signatureResponse.size,
                    signatureLimit,
                    expectedDraftRevision,
                )
            if (handle <= 0L) {
                return GuardedWriteBatchCreateResult.Rejected(mapStatus(handle.toInt()))
            }

            val generation = ops.profileGeneration(handle)
            val revision = ops.draftRevision(handle)
            val count = ops.transactionCount(handle)
            if (generation <= 0L || revision < 0L || count < 0) {
                ops.close(handle)
                val status = when {
                    generation < 0L -> generation.toInt()
                    revision < 0L -> revision.toInt()
                    count < 0 -> count
                    else -> BATCH_INVALID_ARGUMENT
                }
                return GuardedWriteBatchCreateResult.Rejected(mapStatus(status))
            }

            return GuardedWriteBatchCreateResult.Created(
                NativeGuardedWriteBatch(
                    engineHandleProvider = engineHandleProvider,
                    registryHandleProvider = registryHandleProvider,
                    signatureResponse = signatureResponse,
                    signatureLimit = signatureLimit,
                    ops = ops,
                    handle = handle,
                    metadata = GuardedWriteBatchMetadata(generation, revision, count),
                ),
            )
        }

        fun activeCount(): Int = JniNativeGuardedWriteBatchOps.activeCount()

        private fun directBuffer(bytes: ByteArray): ByteBuffer =
            ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                rewind()
            }

        private fun mapStatus(status: Int): GuardedWriteBatchRejection =
            when (status) {
                BATCH_INVALID_HANDLE -> GuardedWriteBatchRejection.InvalidHandle
                BATCH_INVALID_LIMIT -> GuardedWriteBatchRejection.InvalidResponseLimit
                BATCH_EMPTY_SIGNATURE -> GuardedWriteBatchRejection.EmptySignature
                BATCH_INVALID_UTF8 -> GuardedWriteBatchRejection.InvalidUtf8
                BATCH_TOO_LARGE -> GuardedWriteBatchRejection.ResponseTooLarge
                BATCH_UNKNOWN_CONTROLLER -> GuardedWriteBatchRejection.UnknownController
                BATCH_RECOGNIZED_NO_PROFILE -> GuardedWriteBatchRejection.RecognizedControllerWithoutProfile
                BATCH_MISSING_BUNDLE -> GuardedWriteBatchRejection.MissingCompiledBundle
                BATCH_INVALID_ENGINE -> GuardedWriteBatchRejection.InvalidEngine
                BATCH_MISSING_SOURCE -> GuardedWriteBatchRejection.MissingSourceImage
                BATCH_NON_LIVE_SOURCE -> GuardedWriteBatchRejection.NonLiveControllerSource
                BATCH_STALE_REVISION -> GuardedWriteBatchRejection.StaleDraftRevision
                BATCH_STALE_PROFILE -> GuardedWriteBatchRejection.StaleProfileGeneration
                BATCH_SOURCE_CHANGED -> GuardedWriteBatchRejection.SourceImageChanged
                BATCH_PLAN_FAILURE -> GuardedWriteBatchRejection.PlanFailure
                BATCH_INVALID_TRANSACTION_INDEX -> GuardedWriteBatchRejection.InvalidTransactionIndex
                BATCH_OUTPUT_TOO_SMALL -> GuardedWriteBatchRejection.OutputTooSmall
                BATCH_STALE_PRECONDITION -> GuardedWriteBatchRejection.StalePrecondition
                BATCH_WRITE_ACK_FAILURE -> GuardedWriteBatchRejection.WriteAcknowledgementFailure
                BATCH_READBACK_FAILURE -> GuardedWriteBatchRejection.ReadbackFailure
                BATCH_READBACK_MISMATCH -> GuardedWriteBatchRejection.ReadbackMismatch
                BATCH_ENVELOPE_FAILURE -> GuardedWriteBatchRejection.EnvelopeFailure
                BATCH_REGISTRY_FAILURE -> GuardedWriteBatchRejection.RegistryFailure
                BATCH_NOT_DIRECT,
                BATCH_INVALID_LENGTH,
                BATCH_INVALID_ARGUMENT,
                -> GuardedWriteBatchRejection.InvalidArgument
                else -> GuardedWriteBatchRejection.NativeFailure
            }

        private fun GuardedRamWriteStage.nativeCode(): Int =
            when (this) {
                GuardedRamWriteStage.Precondition -> STAGE_PRECONDITION
                GuardedRamWriteStage.Write -> STAGE_WRITE
                GuardedRamWriteStage.Readback -> STAGE_READBACK
            }
    }
}

private object JniNativeGuardedWriteBatchOps : NativeGuardedWriteBatchOps {
    override fun create(
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        expectedDraftRevision: Long,
    ): Long =
        NativeGuardedWriteBatchBridge.create(
            engineHandle,
            registryHandle,
            signatureBuffer,
            signatureLength,
            signatureLimit,
            expectedDraftRevision,
        )

    override fun close(handle: Long) = NativeGuardedWriteBatchBridge.close(handle)

    override fun isAlive(handle: Long): Boolean = NativeGuardedWriteBatchBridge.isAlive(handle)

    override fun activeCount(): Int = NativeGuardedWriteBatchBridge.activeCount()

    override fun profileGeneration(handle: Long): Long = NativeGuardedWriteBatchBridge.profileGeneration(handle)

    override fun draftRevision(handle: Long): Long = NativeGuardedWriteBatchBridge.draftRevision(handle)

    override fun transactionCount(handle: Long): Int = NativeGuardedWriteBatchBridge.transactionCount(handle)

    override fun fillStage(
        batchHandle: Long,
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        transactionIndex: Int,
        stage: Int,
        requestOutput: ByteBuffer,
    ): Long =
        NativeGuardedWriteBatchBridge.fillStage(
            batchHandle,
            engineHandle,
            registryHandle,
            signatureBuffer,
            signatureLength,
            signatureLimit,
            transactionIndex,
            stage,
            requestOutput,
        )

    override fun verifyStage(
        batchHandle: Long,
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        transactionIndex: Int,
        stage: Int,
        responseBuffer: ByteBuffer,
        responseLength: Int,
    ): Int =
        NativeGuardedWriteBatchBridge.verifyStage(
            batchHandle,
            engineHandle,
            registryHandle,
            signatureBuffer,
            signatureLength,
            signatureLimit,
            transactionIndex,
            stage,
            responseBuffer,
            responseLength,
        )
}

object NativeGuardedWriteBatchBridge {
    init {
        System.loadLibrary("epichub_engine")
    }

    external fun create(
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        expectedDraftRevision: Long,
    ): Long

    external fun close(handle: Long)

    external fun isAlive(handle: Long): Boolean

    external fun activeCount(): Int

    external fun profileGeneration(handle: Long): Long

    external fun draftRevision(handle: Long): Long

    external fun transactionCount(handle: Long): Int

    external fun fillStage(
        batchHandle: Long,
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        transactionIndex: Int,
        stage: Int,
        requestOutput: ByteBuffer,
    ): Long

    external fun verifyStage(
        batchHandle: Long,
        engineHandle: Long,
        registryHandle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        transactionIndex: Int,
        stage: Int,
        responseBuffer: ByteBuffer,
        responseLength: Int,
    ): Int
}
