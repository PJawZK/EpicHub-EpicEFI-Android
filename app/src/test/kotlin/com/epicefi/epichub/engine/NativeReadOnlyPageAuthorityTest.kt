package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeReadOnlyPageAuthorityTest {
    @Test
    fun plans_multiple_transfers_from_native_packed_lengths() {
        val ops =
            FakeOps(
                planRequests = listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5)),
                expectedResponses = listOf(10, 12),
            )
        val authority = NativeReadOnlyPageAuthority(handleProvider = { 41L }, ops = ops)
        val signature = "rusEFI exact.signature".toByteArray()

        val result = authority.planPage(signature, signatureLimit = 256, pageIndex = 1)

        val planned = result as ReadOnlyPagePlanResult.Planned
        assertEquals(2, planned.transfers.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), planned.transfers[0].request)
        assertEquals(10, planned.transfers[0].expectedResponseBytes)
        assertArrayEquals(byteArrayOf(4, 5), planned.transfers[1].request)
        assertEquals(12, planned.transfers[1].expectedResponseBytes)
        assertEquals(41L, ops.lastHandle)
        assertEquals(signature.size, ops.lastSignatureLength)
        assertEquals(256, ops.lastSignatureLimit)
        assertEquals(1, ops.lastPageIndex)
        assertTrue(ops.allSignatureBuffersDirect)
        assertTrue(ops.allRequestOutputsDirect)
    }

    @Test
    fun verifies_response_into_payload_without_kotlin_wire_parsing() {
        val ops = FakeOps(verifyPayloads = listOf(byteArrayOf(9, 8, 7)))
        val authority = NativeReadOnlyPageAuthority(handleProvider = { 7L }, ops = ops)
        val response = byteArrayOf(0, 4, 0, 9, 8, 7, 1, 2, 3, 4)

        val result =
            authority.verifyChunk(
                signatureResponse = "sig".toByteArray(),
                signatureLimit = 64,
                pageIndex = 0,
                planIndex = 0,
                responseFrame = response,
            )

        val verified = result as ReadOnlyPageVerifyResult.Verified
        assertArrayEquals(byteArrayOf(9, 8, 7), verified.payload)
        assertEquals(response.size, ops.lastResponseLength)
        assertTrue(ops.lastResponseBufferDirect)
        assertTrue(ops.lastPayloadOutputDirect)
    }

    @Test
    fun native_statuses_remain_explicit_and_fail_closed() {
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
                -11 to ReadOnlyPageAuthorityRejection.PlanFailure,
                -12 to ReadOnlyPageAuthorityRejection.EnvelopeFailure,
                -13 to ReadOnlyPageAuthorityRejection.NonSuccessStatus,
                -14 to ReadOnlyPageAuthorityRejection.PayloadMismatch,
                -15 to ReadOnlyPageAuthorityRejection.OutputTooSmall,
                -16 to ReadOnlyPageAuthorityRejection.InvalidArgument,
                -17 to ReadOnlyPageAuthorityRejection.RegistryFailure,
                -255 to ReadOnlyPageAuthorityRejection.NativeFailure,
            )

        cases.forEach { (status, expected) ->
            val planAuthority =
                NativeReadOnlyPageAuthority(
                    handleProvider = { 1L },
                    ops = FakeOps(planCountOverride = status),
                )
            assertEquals(
                ReadOnlyPagePlanResult.Rejected(expected),
                planAuthority.planPage("sig".toByteArray(), 64, 0),
            )

            val verifyAuthority =
                NativeReadOnlyPageAuthority(
                    handleProvider = { 1L },
                    ops = FakeOps(verifyResultOverride = status),
                )
            assertEquals(
                ReadOnlyPageVerifyResult.Rejected(expected),
                verifyAuthority.verifyChunk(
                    "sig".toByteArray(),
                    64,
                    0,
                    0,
                    byteArrayOf(1),
                ),
            )
        }
    }

    @Test
    fun closed_registry_handle_fails_before_native_calls() {
        val ops = FakeOps(planRequests = listOf(byteArrayOf(1)), expectedResponses = listOf(8))
        val authority = NativeReadOnlyPageAuthority(handleProvider = { 0L }, ops = ops)

        assertEquals(
            ReadOnlyPagePlanResult.Rejected(ReadOnlyPageAuthorityRejection.InvalidHandle),
            authority.planPage("sig".toByteArray(), 64, 0),
        )
        assertEquals(
            ReadOnlyPageVerifyResult.Rejected(ReadOnlyPageAuthorityRejection.InvalidHandle),
            authority.verifyChunk("sig".toByteArray(), 64, 0, 0, byteArrayOf(1)),
        )
        assertEquals(0, ops.planCountCalls)
        assertEquals(0, ops.fillCalls)
        assertEquals(0, ops.verifyCalls)
    }

    private class FakeOps(
        private val planRequests: List<ByteArray> = listOf(byteArrayOf(1)),
        private val expectedResponses: List<Int> = listOf(8),
        private val verifyPayloads: List<ByteArray> = listOf(byteArrayOf(1)),
        private val planCountOverride: Int? = null,
        private val verifyResultOverride: Int? = null,
    ) : NativeReadOnlyPageOps {
        var planCountCalls = 0
        var fillCalls = 0
        var verifyCalls = 0
        var lastHandle = -1L
        var lastSignatureLength = -1
        var lastSignatureLimit = -1
        var lastPageIndex = -1
        var lastResponseLength = -1
        var allSignatureBuffersDirect = true
        var allRequestOutputsDirect = true
        var lastResponseBufferDirect = false
        var lastPayloadOutputDirect = false

        override fun pageReadPlanCount(
            handle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
            pageIndex: Int,
            maxResponseFrameBytes: Int,
        ): Int {
            planCountCalls += 1
            captureCommon(handle, signatureBuffer, signatureLength, signatureLimit, pageIndex)
            assertEquals(65_536, maxResponseFrameBytes)
            return planCountOverride ?: planRequests.size
        }

        override fun fillPageReadPlan(
            handle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
            pageIndex: Int,
            maxResponseFrameBytes: Int,
            planIndex: Int,
            requestOutput: ByteBuffer,
        ): Long {
            fillCalls += 1
            captureCommon(handle, signatureBuffer, signatureLength, signatureLimit, pageIndex)
            allRequestOutputsDirect = allRequestOutputsDirect && requestOutput.isDirect
            val request = planRequests[planIndex]
            requestOutput.put(request)
            val expected = expectedResponses[planIndex]
            return (request.size.toLong() shl 32) or expected.toLong()
        }

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
        ): Int {
            verifyCalls += 1
            captureCommon(handle, signatureBuffer, signatureLength, signatureLimit, pageIndex)
            lastResponseLength = responseLength
            lastResponseBufferDirect = responseBuffer.isDirect
            lastPayloadOutputDirect = payloadOutput.isDirect
            verifyResultOverride?.let { return it }
            val payload = verifyPayloads[planIndex]
            payloadOutput.put(payload)
            return payload.size
        }

        private fun captureCommon(
            handle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
            pageIndex: Int,
        ) {
            lastHandle = handle
            lastSignatureLength = signatureLength
            lastSignatureLimit = signatureLimit
            lastPageIndex = pageIndex
            allSignatureBuffersDirect = allSignatureBuffersDirect && signatureBuffer.isDirect
        }
    }
}
