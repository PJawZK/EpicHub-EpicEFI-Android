package com.epicefi.epichub.engine

import java.nio.ByteBuffer

private const val MAX_GUARDED_REQUEST_FRAME_BYTES = 65_541
private const val MAX_GUARDED_RESPONSE_FRAME_BYTES = 64 * 1024
private const val MAX_GUARDED_PATCH_BYTES = 64 * 1024

private const val WRITE_INVALID_HANDLE = -1
private const val WRITE_NOT_DIRECT = -2
private const val WRITE_INVALID_LENGTH = -3
private const val WRITE_INVALID_LIMIT = -4
private const val WRITE_EMPTY_SIGNATURE = -5
private const val WRITE_INVALID_UTF8 = -6
private const val WRITE_TOO_LARGE = -7
private const val WRITE_UNKNOWN_CONTROLLER = -8
private const val WRITE_RECOGNIZED_NO_PROFILE = -9
private const val WRITE_MISSING_BUNDLE = -10
private const val WRITE_PLAN_ERROR = -11
private const val WRITE_ENVELOPE_ERROR = -12
private const val WRITE_PRECONDITION_FAILURE = -13
private const val WRITE_STALE_PRECONDITION = -14
private const val WRITE_ACK_FAILURE = -15
private const val WRITE_READBACK_FAILURE = -16
private const val WRITE_READBACK_MISMATCH = -17
private const val WRITE_OUTPUT_TOO_SMALL = -18
private const val WRITE_INVALID_ARGUMENT = -19
private const val WRITE_REGISTRY_ERROR = -20

private const val STAGE_PRECONDITION = 0
private const val STAGE_WRITE = 1
private const val STAGE_READBACK = 2

internal interface NativeGuardedRamWriteOps {
    fun guardedWritePlanCount(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        page: Int,
        offset: Int,
        beforeBuffer: ByteBuffer,
        beforeLength: Int,
        afterBuffer: ByteBuffer,
        afterLength: Int,
    ): Int

    fun fillGuardedWriteStage(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        page: Int,
        offset: Int,
        beforeBuffer: ByteBuffer,
        beforeLength: Int,
        afterBuffer: ByteBuffer,
        afterLength: Int,
        planIndex: Int,
        stage: Int,
        requestOutput: ByteBuffer,
    ): Long

    fun verifyGuardedWriteStage(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        page: Int,
        offset: Int,
        beforeBuffer: ByteBuffer,
        beforeLength: Int,
        afterBuffer: ByteBuffer,
        afterLength: Int,
        planIndex: Int,
        stage: Int,
        responseBuffer: ByteBuffer,
        responseLength: Int,
    ): Int
}

internal class NativeGuardedRamWriteAuthority(
    private val handleProvider: () -> Long,
    private val ops: NativeGuardedRamWriteOps = JniNativeGuardedRamWriteOps,
) : GuardedRamWriteAuthority {
    constructor(registry: NativeProfileRegistry) : this(registry::readOnlyRegistryHandle)

    override fun plan(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        intent: GuardedRamWriteIntent,
    ): GuardedRamWritePlanResult {
        val validation = validateCommon(signatureLimit, intent)
        if (validation != null) {
            return GuardedRamWritePlanResult.Rejected(validation)
        }
        val handle = handleProvider()
        if (handle == 0L) {
            return GuardedRamWritePlanResult.Rejected(GuardedRamWriteAuthorityRejection.InvalidHandle)
        }

        val buffers = NativeInputs(signatureResponse, intent)
        val count =
            ops.guardedWritePlanCount(
                handle,
                buffers.signature,
                signatureResponse.size,
                signatureLimit,
                intent.page,
                intent.offset,
                buffers.before,
                intent.before.size,
                buffers.after,
                intent.after.size,
            )
        if (count <= 0) {
            return GuardedRamWritePlanResult.Rejected(mapStatus(count))
        }

        val transfers = ArrayList<GuardedRamWriteTransferPlan>(count)
        repeat(count) { planIndex ->
            val precondition =
                fillStage(handle, buffers, signatureResponse.size, signatureLimit, intent, planIndex, STAGE_PRECONDITION)
                    ?: return GuardedRamWritePlanResult.Rejected(lastFillFailure)
            val write =
                fillStage(handle, buffers, signatureResponse.size, signatureLimit, intent, planIndex, STAGE_WRITE)
                    ?: return GuardedRamWritePlanResult.Rejected(lastFillFailure)
            val readback =
                fillStage(handle, buffers, signatureResponse.size, signatureLimit, intent, planIndex, STAGE_READBACK)
                    ?: return GuardedRamWritePlanResult.Rejected(lastFillFailure)
            transfers += GuardedRamWriteTransferPlan(precondition, write, readback)
        }
        return GuardedRamWritePlanResult.Planned(transfers)
    }

    override fun verifyPrecondition(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        intent: GuardedRamWriteIntent,
        planIndex: Int,
        responseFrame: ByteArray,
    ): GuardedRamWriteVerifyResult =
        verifyStage(
            signatureResponse,
            signatureLimit,
            intent,
            planIndex,
            STAGE_PRECONDITION,
            responseFrame,
        )

    override fun verifyWriteAcknowledgement(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        intent: GuardedRamWriteIntent,
        planIndex: Int,
        responseFrame: ByteArray,
    ): GuardedRamWriteVerifyResult =
        verifyStage(
            signatureResponse,
            signatureLimit,
            intent,
            planIndex,
            STAGE_WRITE,
            responseFrame,
        )

    override fun verifyReadback(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        intent: GuardedRamWriteIntent,
        planIndex: Int,
        responseFrame: ByteArray,
    ): GuardedRamWriteVerifyResult =
        verifyStage(
            signatureResponse,
            signatureLimit,
            intent,
            planIndex,
            STAGE_READBACK,
            responseFrame,
        )

    private var lastFillFailure: GuardedRamWriteAuthorityRejection = GuardedRamWriteAuthorityRejection.NativeFailure

    private fun fillStage(
        handle: Long,
        buffers: NativeInputs,
        signatureLength: Int,
        signatureLimit: Int,
        intent: GuardedRamWriteIntent,
        planIndex: Int,
        stage: Int,
    ): GuardedRamWriteStagePlan? {
        val output = ByteBuffer.allocateDirect(MAX_GUARDED_REQUEST_FRAME_BYTES)
        val packed =
            ops.fillGuardedWriteStage(
                handle,
                buffers.signature,
                signatureLength,
                signatureLimit,
                intent.page,
                intent.offset,
                buffers.before,
                intent.before.size,
                buffers.after,
                intent.after.size,
                planIndex,
                stage,
                output,
            )
        if (packed <= 0L) {
            lastFillFailure = mapStatus(packed.toInt())
            return null
        }
        val requestLength = (packed ushr 32).toInt()
        val expectedResponseBytes = (packed and 0xffff_ffffL).toInt()
        if (
            requestLength <= 0 || requestLength > MAX_GUARDED_REQUEST_FRAME_BYTES ||
                expectedResponseBytes <= 0 || expectedResponseBytes > MAX_GUARDED_RESPONSE_FRAME_BYTES
        ) {
            lastFillFailure = GuardedRamWriteAuthorityRejection.NativeFailure
            return null
        }
        output.position(0)
        val request = ByteArray(requestLength)
        output.get(request)
        return GuardedRamWriteStagePlan(request, expectedResponseBytes)
    }

    private fun verifyStage(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        intent: GuardedRamWriteIntent,
        planIndex: Int,
        stage: Int,
        responseFrame: ByteArray,
    ): GuardedRamWriteVerifyResult {
        val validation = validateCommon(signatureLimit, intent)
        if (validation != null || planIndex < 0 || responseFrame.isEmpty() || responseFrame.size > MAX_GUARDED_RESPONSE_FRAME_BYTES) {
            return GuardedRamWriteVerifyResult.Rejected(
                validation ?: GuardedRamWriteAuthorityRejection.InvalidArgument,
            )
        }
        val handle = handleProvider()
        if (handle == 0L) {
            return GuardedRamWriteVerifyResult.Rejected(GuardedRamWriteAuthorityRejection.InvalidHandle)
        }
        val buffers = NativeInputs(signatureResponse, intent)
        val response = directBuffer(responseFrame)
        val status =
            ops.verifyGuardedWriteStage(
                handle,
                buffers.signature,
                signatureResponse.size,
                signatureLimit,
                intent.page,
                intent.offset,
                buffers.before,
                intent.before.size,
                buffers.after,
                intent.after.size,
                planIndex,
                stage,
                response,
                responseFrame.size,
            )
        return if (status == 0) {
            GuardedRamWriteVerifyResult.Verified
        } else {
            GuardedRamWriteVerifyResult.Rejected(mapStatus(status))
        }
    }

    private fun validateCommon(
        signatureLimit: Int,
        intent: GuardedRamWriteIntent,
    ): GuardedRamWriteAuthorityRejection? =
        if (
            signatureLimit <= 0 || intent.page <= 0 || intent.offset < 0 ||
                intent.before.isEmpty() || intent.before.size != intent.after.size ||
                intent.before.size > MAX_GUARDED_PATCH_BYTES
        ) {
            GuardedRamWriteAuthorityRejection.InvalidArgument
        } else {
            null
        }

    private fun mapStatus(status: Int): GuardedRamWriteAuthorityRejection =
        when (status) {
            WRITE_INVALID_HANDLE -> GuardedRamWriteAuthorityRejection.InvalidHandle
            WRITE_UNKNOWN_CONTROLLER -> GuardedRamWriteAuthorityRejection.UnknownController
            WRITE_RECOGNIZED_NO_PROFILE -> GuardedRamWriteAuthorityRejection.RecognizedControllerWithoutProfile
            WRITE_MISSING_BUNDLE -> GuardedRamWriteAuthorityRejection.MissingCompiledBundle
            WRITE_PLAN_ERROR -> GuardedRamWriteAuthorityRejection.PlanFailure
            WRITE_ENVELOPE_ERROR -> GuardedRamWriteAuthorityRejection.EnvelopeFailure
            WRITE_PRECONDITION_FAILURE -> GuardedRamWriteAuthorityRejection.EnvelopeFailure
            WRITE_STALE_PRECONDITION -> GuardedRamWriteAuthorityRejection.StalePrecondition
            WRITE_ACK_FAILURE -> GuardedRamWriteAuthorityRejection.WriteAcknowledgementFailure
            WRITE_READBACK_FAILURE -> GuardedRamWriteAuthorityRejection.EnvelopeFailure
            WRITE_READBACK_MISMATCH -> GuardedRamWriteAuthorityRejection.ReadbackMismatch
            WRITE_REGISTRY_ERROR -> GuardedRamWriteAuthorityRejection.RegistryFailure
            WRITE_NOT_DIRECT,
            WRITE_INVALID_LENGTH,
            WRITE_INVALID_LIMIT,
            WRITE_EMPTY_SIGNATURE,
            WRITE_INVALID_UTF8,
            WRITE_TOO_LARGE,
            WRITE_OUTPUT_TOO_SMALL,
            WRITE_INVALID_ARGUMENT,
            -> GuardedRamWriteAuthorityRejection.InvalidArgument
            else -> GuardedRamWriteAuthorityRejection.NativeFailure
        }

    private class NativeInputs(
        signatureResponse: ByteArray,
        intent: GuardedRamWriteIntent,
    ) {
        val signature = directBuffer(signatureResponse)
        val before = directBuffer(intent.before)
        val after = directBuffer(intent.after)
    }

    companion object {
        private fun directBuffer(bytes: ByteArray): ByteBuffer =
            ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                rewind()
            }
    }
}

private object JniNativeGuardedRamWriteOps : NativeGuardedRamWriteOps {
    override fun guardedWritePlanCount(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        page: Int,
        offset: Int,
        beforeBuffer: ByteBuffer,
        beforeLength: Int,
        afterBuffer: ByteBuffer,
        afterLength: Int,
    ): Int =
        NativeGuardedRamWriteBridge.guardedWritePlanCount(
            handle,
            signatureBuffer,
            signatureLength,
            signatureLimit,
            page,
            offset,
            beforeBuffer,
            beforeLength,
            afterBuffer,
            afterLength,
        )

    override fun fillGuardedWriteStage(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        page: Int,
        offset: Int,
        beforeBuffer: ByteBuffer,
        beforeLength: Int,
        afterBuffer: ByteBuffer,
        afterLength: Int,
        planIndex: Int,
        stage: Int,
        requestOutput: ByteBuffer,
    ): Long =
        NativeGuardedRamWriteBridge.fillGuardedWriteStage(
            handle,
            signatureBuffer,
            signatureLength,
            signatureLimit,
            page,
            offset,
            beforeBuffer,
            beforeLength,
            afterBuffer,
            afterLength,
            planIndex,
            stage,
            requestOutput,
        )

    override fun verifyGuardedWriteStage(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        page: Int,
        offset: Int,
        beforeBuffer: ByteBuffer,
        beforeLength: Int,
        afterBuffer: ByteBuffer,
        afterLength: Int,
        planIndex: Int,
        stage: Int,
        responseBuffer: ByteBuffer,
        responseLength: Int,
    ): Int =
        NativeGuardedRamWriteBridge.verifyGuardedWriteStage(
            handle,
            signatureBuffer,
            signatureLength,
            signatureLimit,
            page,
            offset,
            beforeBuffer,
            beforeLength,
            afterBuffer,
            afterLength,
            planIndex,
            stage,
            responseBuffer,
            responseLength,
        )
}

object NativeGuardedRamWriteBridge {
    init {
        System.loadLibrary("epichub_engine")
    }

    external fun guardedWritePlanCount(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        page: Int,
        offset: Int,
        beforeBuffer: ByteBuffer,
        beforeLength: Int,
        afterBuffer: ByteBuffer,
        afterLength: Int,
    ): Int

    external fun fillGuardedWriteStage(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        page: Int,
        offset: Int,
        beforeBuffer: ByteBuffer,
        beforeLength: Int,
        afterBuffer: ByteBuffer,
        afterLength: Int,
        planIndex: Int,
        stage: Int,
        requestOutput: ByteBuffer,
    ): Long

    external fun verifyGuardedWriteStage(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        page: Int,
        offset: Int,
        beforeBuffer: ByteBuffer,
        beforeLength: Int,
        afterBuffer: ByteBuffer,
        afterLength: Int,
        planIndex: Int,
        stage: Int,
        responseBuffer: ByteBuffer,
        responseLength: Int,
    ): Int
}
