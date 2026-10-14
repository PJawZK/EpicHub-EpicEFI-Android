package com.epicefi.epichub.engine

import java.nio.ByteBuffer

private const val MAX_RESPONSE_FRAME_BYTES = 64 * 1024
private const val MAX_REQUEST_FRAME_BYTES = 65_541

private const val ACQ_INVALID_HANDLE = -1
private const val ACQ_NOT_DIRECT = -2
private const val ACQ_INVALID_LENGTH = -3
private const val ACQ_INVALID_LIMIT = -4
private const val ACQ_EMPTY_SIGNATURE = -5
private const val ACQ_INVALID_UTF8 = -6
private const val ACQ_TOO_LARGE = -7
private const val ACQ_UNKNOWN_CONTROLLER = -8
private const val ACQ_RECOGNIZED_NO_PROFILE = -9
private const val ACQ_MISSING_BUNDLE = -10
private const val ACQ_PLAN_ERROR = -11
private const val ACQ_ENVELOPE_ERROR = -12
private const val ACQ_NON_SUCCESS_STATUS = -13
private const val ACQ_PAYLOAD_MISMATCH = -14
private const val ACQ_OUTPUT_TOO_SMALL = -15
private const val ACQ_INVALID_ARGUMENT = -16
private const val ACQ_REGISTRY_ERROR = -17

data class ReadOnlyPageTransferPlan(
    val request: ByteArray,
    val expectedResponseBytes: Int,
)

enum class ReadOnlyPageAuthorityRejection {
    InvalidHandle,
    NotDirectBuffer,
    InvalidLength,
    InvalidResponseLimit,
    EmptySignature,
    InvalidUtf8,
    ResponseTooLarge,
    UnknownController,
    RecognizedControllerWithoutProfile,
    MissingCompiledBundle,
    PlanFailure,
    EnvelopeFailure,
    NonSuccessStatus,
    PayloadMismatch,
    OutputTooSmall,
    InvalidArgument,
    RegistryFailure,
    NativeFailure,
}

sealed interface ReadOnlyPagePlanResult {
    data class Planned(
        val transfers: List<ReadOnlyPageTransferPlan>,
    ) : ReadOnlyPagePlanResult

    data class Rejected(
        val reason: ReadOnlyPageAuthorityRejection,
    ) : ReadOnlyPagePlanResult
}

sealed interface ReadOnlyPageVerifyResult {
    data class Verified(
        val payload: ByteArray,
    ) : ReadOnlyPageVerifyResult

    data class Rejected(
        val reason: ReadOnlyPageAuthorityRejection,
    ) : ReadOnlyPageVerifyResult
}

internal interface ReadOnlyPageAuthority {
    fun planPage(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        pageIndex: Int,
    ): ReadOnlyPagePlanResult

    fun verifyChunk(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        pageIndex: Int,
        planIndex: Int,
        responseFrame: ByteArray,
    ): ReadOnlyPageVerifyResult
}

internal interface NativeReadOnlyPageOps {
    fun pageReadPlanCount(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        pageIndex: Int,
        maxResponseFrameBytes: Int,
    ): Int

    fun fillPageReadPlan(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        pageIndex: Int,
        maxResponseFrameBytes: Int,
        planIndex: Int,
        requestOutput: ByteBuffer,
    ): Long

    fun verifyPageReadResponse(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        pageIndex: Int,
        maxResponseFrameBytes: Int,
        planIndex: Int,
        responseBuffer: ByteBuffer,
        responseLength: Int,
        payloadOutput: ByteBuffer,
    ): Int
}

internal class NativeReadOnlyPageAuthority(
    private val handleProvider: () -> Long,
    private val ops: NativeReadOnlyPageOps = JniNativeReadOnlyPageOps,
) : ReadOnlyPageAuthority {
    override fun planPage(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        pageIndex: Int,
    ): ReadOnlyPagePlanResult {
        val handle = handleProvider()
        if (handle == 0L) {
            return rejectedPlan(ReadOnlyPageAuthorityRejection.InvalidHandle)
        }
        if (signatureLimit <= 0 || pageIndex < 0) {
            return rejectedPlan(ReadOnlyPageAuthorityRejection.InvalidArgument)
        }

        val signatureBuffer = directBuffer(signatureResponse)
        val count =
            ops.pageReadPlanCount(
                handle,
                signatureBuffer,
                signatureResponse.size,
                signatureLimit,
                pageIndex,
                MAX_RESPONSE_FRAME_BYTES,
            )
        if (count <= 0) {
            return rejectedPlan(mapStatus(count))
        }

        val requestBuffer = ByteBuffer.allocateDirect(MAX_REQUEST_FRAME_BYTES)
        val transfers = ArrayList<ReadOnlyPageTransferPlan>(count)
        repeat(count) { planIndex ->
            requestBuffer.clear()
            val packed =
                ops.fillPageReadPlan(
                    handle,
                    signatureBuffer,
                    signatureResponse.size,
                    signatureLimit,
                    pageIndex,
                    MAX_RESPONSE_FRAME_BYTES,
                    planIndex,
                    requestBuffer,
                )
            if (packed <= 0L) {
                return rejectedPlan(mapStatus(packed.toInt()))
            }

            val requestLength = (packed ushr 32).toInt()
            val expectedResponseBytes = (packed and 0xffff_ffffL).toInt()
            if (
                requestLength <= 0 || requestLength > MAX_REQUEST_FRAME_BYTES ||
                    expectedResponseBytes <= 0 || expectedResponseBytes > MAX_RESPONSE_FRAME_BYTES
            ) {
                return rejectedPlan(ReadOnlyPageAuthorityRejection.NativeFailure)
            }

            requestBuffer.position(0)
            val request = ByteArray(requestLength)
            requestBuffer.get(request)
            transfers += ReadOnlyPageTransferPlan(request, expectedResponseBytes)
        }
        return ReadOnlyPagePlanResult.Planned(transfers)
    }

    override fun verifyChunk(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        pageIndex: Int,
        planIndex: Int,
        responseFrame: ByteArray,
    ): ReadOnlyPageVerifyResult {
        val handle = handleProvider()
        if (handle == 0L) {
            return rejectedVerify(ReadOnlyPageAuthorityRejection.InvalidHandle)
        }
        if (
            signatureLimit <= 0 || pageIndex < 0 || planIndex < 0 ||
                responseFrame.isEmpty() || responseFrame.size > MAX_RESPONSE_FRAME_BYTES
        ) {
            return rejectedVerify(ReadOnlyPageAuthorityRejection.InvalidArgument)
        }

        val signatureBuffer = directBuffer(signatureResponse)
        val responseBuffer = directBuffer(responseFrame)
        val payloadOutput = ByteBuffer.allocateDirect(MAX_RESPONSE_FRAME_BYTES)
        val result =
            ops.verifyPageReadResponse(
                handle,
                signatureBuffer,
                signatureResponse.size,
                signatureLimit,
                pageIndex,
                MAX_RESPONSE_FRAME_BYTES,
                planIndex,
                responseBuffer,
                responseFrame.size,
                payloadOutput,
            )
        if (result < 0) {
            return rejectedVerify(mapStatus(result))
        }
        if (result > MAX_RESPONSE_FRAME_BYTES) {
            return rejectedVerify(ReadOnlyPageAuthorityRejection.NativeFailure)
        }

        payloadOutput.position(0)
        val payload = ByteArray(result)
        payloadOutput.get(payload)
        return ReadOnlyPageVerifyResult.Verified(payload)
    }

    private fun directBuffer(bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            rewind()
        }

    private fun mapStatus(status: Int): ReadOnlyPageAuthorityRejection =
        when (status) {
            ACQ_INVALID_HANDLE -> ReadOnlyPageAuthorityRejection.InvalidHandle
            ACQ_NOT_DIRECT -> ReadOnlyPageAuthorityRejection.NotDirectBuffer
            ACQ_INVALID_LENGTH -> ReadOnlyPageAuthorityRejection.InvalidLength
            ACQ_INVALID_LIMIT -> ReadOnlyPageAuthorityRejection.InvalidResponseLimit
            ACQ_EMPTY_SIGNATURE -> ReadOnlyPageAuthorityRejection.EmptySignature
            ACQ_INVALID_UTF8 -> ReadOnlyPageAuthorityRejection.InvalidUtf8
            ACQ_TOO_LARGE -> ReadOnlyPageAuthorityRejection.ResponseTooLarge
            ACQ_UNKNOWN_CONTROLLER -> ReadOnlyPageAuthorityRejection.UnknownController
            ACQ_RECOGNIZED_NO_PROFILE -> ReadOnlyPageAuthorityRejection.RecognizedControllerWithoutProfile
            ACQ_MISSING_BUNDLE -> ReadOnlyPageAuthorityRejection.MissingCompiledBundle
            ACQ_PLAN_ERROR -> ReadOnlyPageAuthorityRejection.PlanFailure
            ACQ_ENVELOPE_ERROR -> ReadOnlyPageAuthorityRejection.EnvelopeFailure
            ACQ_NON_SUCCESS_STATUS -> ReadOnlyPageAuthorityRejection.NonSuccessStatus
            ACQ_PAYLOAD_MISMATCH -> ReadOnlyPageAuthorityRejection.PayloadMismatch
            ACQ_OUTPUT_TOO_SMALL -> ReadOnlyPageAuthorityRejection.OutputTooSmall
            ACQ_INVALID_ARGUMENT -> ReadOnlyPageAuthorityRejection.InvalidArgument
            ACQ_REGISTRY_ERROR -> ReadOnlyPageAuthorityRejection.RegistryFailure
            else -> ReadOnlyPageAuthorityRejection.NativeFailure
        }

    private fun rejectedPlan(reason: ReadOnlyPageAuthorityRejection) =
        ReadOnlyPagePlanResult.Rejected(reason)

    private fun rejectedVerify(reason: ReadOnlyPageAuthorityRejection) =
        ReadOnlyPageVerifyResult.Rejected(reason)
}

private object JniNativeReadOnlyPageOps : NativeReadOnlyPageOps {
    override fun pageReadPlanCount(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        pageIndex: Int,
        maxResponseFrameBytes: Int,
    ): Int =
        NativeReadOnlyPageBridge.pageReadPlanCount(
            handle,
            signatureBuffer,
            signatureLength,
            signatureLimit,
            pageIndex,
            maxResponseFrameBytes,
        )

    override fun fillPageReadPlan(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        pageIndex: Int,
        maxResponseFrameBytes: Int,
        planIndex: Int,
        requestOutput: ByteBuffer,
    ): Long =
        NativeReadOnlyPageBridge.fillPageReadPlan(
            handle,
            signatureBuffer,
            signatureLength,
            signatureLimit,
            pageIndex,
            maxResponseFrameBytes,
            planIndex,
            requestOutput,
        )

    override fun verifyPageReadResponse(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        pageIndex: Int,
        maxResponseFrameBytes: Int,
        planIndex: Int,
        responseBuffer: ByteBuffer,
        responseLength: Int,
        payloadOutput: ByteBuffer,
    ): Int =
        NativeReadOnlyPageBridge.verifyPageReadResponse(
            handle,
            signatureBuffer,
            signatureLength,
            signatureLimit,
            pageIndex,
            maxResponseFrameBytes,
            planIndex,
            responseBuffer,
            responseLength,
            payloadOutput,
        )
}

object NativeReadOnlyPageBridge {
    init {
        System.loadLibrary("epichub_engine")
    }

    external fun pageReadPlanCount(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        pageIndex: Int,
        maxResponseFrameBytes: Int,
    ): Int

    external fun fillPageReadPlan(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        pageIndex: Int,
        maxResponseFrameBytes: Int,
        planIndex: Int,
        requestOutput: ByteBuffer,
    ): Long

    external fun verifyPageReadResponse(
        handle: Long,
        signatureBuffer: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        pageIndex: Int,
        maxResponseFrameBytes: Int,
        planIndex: Int,
        responseBuffer: ByteBuffer,
        responseLength: Int,
        payloadOutput: ByteBuffer,
    ): Int
}
